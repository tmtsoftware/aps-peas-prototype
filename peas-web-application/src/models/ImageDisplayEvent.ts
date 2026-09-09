import type { Event } from '@tmtsoftware/esw-ts'
import { stringKey } from '@tmtsoftware/esw-ts'

// Matches ICD-SDB-APS-APS_CCR03.pdf SS35.1.2 exactly -- see
// PeasExposureServiceImpl.scala's ImageDisplayType/publishImageDisplayEvent
// for the publishing side.
export type ImageDisplayEventType = 'IMAGE' | 'IMAGE_OVERLAY'

export interface ApsImageDisplayEvent {
  type: ImageDisplayEventType
  dialogKey: string
  helpKey: string
  imageFilename: string
  eventTime: string
  source: string
  // Only meaningful for IMAGE_OVERLAY-type events. peas-exposure-service
  // only ever publishes plain IMAGE events today (see
  // PeasExposureServiceImpl.scala), so this is undefined in practice for now.
  imageOverlayData?: string
}

const typeKey             = stringKey('type')
const dialogKeyKey        = stringKey('dialogKey')
const helpKeyKey          = stringKey('helpKey')
const imageFilenameKey    = stringKey('imageFilename')
const imageOverlayDataKey = stringKey('imageOverlayData')

export const decodeApsImageDisplayEvent = (
  event: Event
): ApsImageDisplayEvent | undefined => {
  const type            = event.get(typeKey)?.values[0]
  const dialogKey        = event.get(dialogKeyKey)?.values[0]
  const helpKey          = event.get(helpKeyKey)?.values[0]
  const imageFilename    = event.get(imageFilenameKey)?.values[0]
  const imageOverlayData = event.get(imageOverlayDataKey)?.values[0]

  if (!type || !dialogKey || !helpKey || !imageFilename) return undefined

  return {
    type: type as ImageDisplayEventType,
    dialogKey,
    helpKey,
    imageFilename,
    eventTime: event.eventTime,
    source: `${event.source.subsystem}.${event.source.componentName}`,
    ...(imageOverlayData ? { imageOverlayData } : {})
  }
}
