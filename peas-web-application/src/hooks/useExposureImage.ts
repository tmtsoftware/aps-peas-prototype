import { EventKey, EventName, EventService, Prefix } from '@tmtsoftware/esw-ts'
import type { Subscription } from '@tmtsoftware/esw-ts'
import { useEffect, useRef, useState } from 'react'
import { decodeApsImageDisplayEvent } from '../models/ImageDisplayEvent'
import { getExposureBackendUrl } from '../utils/resolveBackend'
import { getBlob } from '../utils/Http'
import { useLocationService } from '../contexts/LocationServiceContext'

const IMAGE_DISPLAY_EVENT_NAME = new EventName('apsImageDisplayEvent')

// APS.PeasExposureService -- must match PeasExposureServiceImpl.scala's
// ImageDisplayEventSourcePrefix exactly, or this subscription silently
// never matches anything.
const IMAGE_DISPLAY_EVENT_KEY = new EventKey(
  Prefix.fromString('APS.PeasExposureService'),
  IMAGE_DISPLAY_EVENT_NAME
)

// Subscribes to apsImageDisplayEvent; on receipt, resolves
// peas-exposure-service's location and fetches the named exposure's
// low-res PNG from GET /retrieveLowResImage?apsFilename=... (raw
// image/png bytes per the ICD, not JSON -- see Http.ts's getBlob).
//
// The first event delivered on each subscription is always discarded --
// CSW delivers the last known event immediately on subscribe regardless of
// how old it is, so acting on it risks showing a stale image left over
// from a previous run before this run's own first exposure has happened.
// See the isFirstEvent handling below.
//
// Deliberately does NOT track "the current image" itself -- apsImageDisplayEvent
// carries no iteration number (see ICD SS35.1.2's payload: type/dialogKey/helpKey/
// imageFilename/imageOverlayData only), so this hook has no way to know which
// iteration a given image belongs to. That attribution has to happen in the
// caller, which knows the currently-running iteration at the moment each image
// arrives (see SequenceSubmitter.tsx's handleExposureImageReady / currentIterationRef).
// Every fetched image is reported via onImageReady as an object URL; the caller
// owns that URL's lifecycle (when to revoke it) since only the caller knows
// whether it's still needed for a still-visible iteration tab.
export const useExposureImage = (
  active: boolean,
  onImageReady: (url: string, filename: string) => void
) => {
  const locationService = useLocationService()
  const [error, setError] = useState<string | undefined>(undefined)
  const subscriptionRef = useRef<Subscription | undefined>(undefined)
  const onImageReadyRef = useRef(onImageReady)
  onImageReadyRef.current = onImageReady

  useEffect(() => {
    if (!active) {
      subscriptionRef.current?.cancel()
      subscriptionRef.current = undefined
      return
    }

    let cancelled = false
    // CSW's event service always delivers the last known event immediately on
    // subscribe, even if it's leftover from a previous run's last iteration --
    // there's no way to distinguish "this is the pre-existing cached value" from
    // "this is a genuinely fresh publish" by looking at the event itself. But we
    // know structurally that the first delivery on a brand-new subscription can
    // never be a real image from THIS run (this run's own takeGoodExposure hasn't
    // had time to complete yet -- it delays 5s specifically to make this timing
    // assumption safe, see CommonD.kt). So the first delivery is simply discarded
    // outright, never fetched or reported; only the 2nd+ delivery within this
    // subscription's lifetime is treated as real.
    let isFirstEvent = true

    const fetchAndDisplay = async (filename: string) => {
      try {
        const baseUrl = await getExposureBackendUrl(locationService)
        if (!baseUrl) throw new Error('Exposure Service backend not available')
        const blob = await getBlob(
          `${baseUrl}retrieveLowResImage?apsFilename=${encodeURIComponent(filename)}`
        )
        if (cancelled) return
        onImageReadyRef.current(URL.createObjectURL(blob), filename)
      } catch (e) {
        console.error('useExposureImage: failed to fetch low-res image', e)
        if (!cancelled) {
          setError(
            `Failed to fetch exposure image: ${e instanceof Error ? e.message : String(e)}`
          )
        }
      }
    }

    const startSubscription = async () => {
      try {
        console.log('useExposureImage: creating EventService...')
        const eventService = await EventService()
        if (cancelled) return

        console.log('useExposureImage: subscribing to', IMAGE_DISPLAY_EVENT_KEY)
        subscriptionRef.current = eventService.subscribe(new Set([IMAGE_DISPLAY_EVENT_KEY]))(
          (event) => {
            if (isFirstEvent) {
              isFirstEvent = false
              console.log('useExposureImage: discarding initial (pre-existing) apsImageDisplayEvent delivered on subscribe')
              return
            }
            const decoded = decodeApsImageDisplayEvent(event)
            if (!decoded) return
            console.log('useExposureImage: received apsImageDisplayEvent', decoded)
            fetchAndDisplay(decoded.imageFilename)
          },
          (err) => {
            console.error('useExposureImage: subscription error', err)
            setError(`Event subscription error: ${err.message}`)
          },
          () => {
            console.log('useExposureImage: subscription closed')
          }
        )
      } catch (e) {
        console.error('useExposureImage: failed to start', e)
        if (!cancelled) {
          setError(
            `Failed to connect to Event Service: ${e instanceof Error ? e.message : String(e)}`
          )
        }
      }
    }

    startSubscription()

    return () => {
      cancelled = true
      subscriptionRef.current?.cancel()
      subscriptionRef.current = undefined
    }
  }, [active, locationService])

  return { error }
}
