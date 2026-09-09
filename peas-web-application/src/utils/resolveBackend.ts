import { HttpConnection, LocationService, Prefix } from '@tmtsoftware/esw-ts'
import { errorMessage } from './message'

const backendServicePrefix = Prefix.fromString('APS.PeasProcedureSetupService')
export const BACKEND_CONNECTION = HttpConnection(
  backendServicePrefix,
  'Service'
)

export const getBackendUrl = (
  locationService: LocationService
): Promise<string | undefined> =>
  getBackendLocation(locationService).then((location) => location?.uri)

const getBackendLocation = async (locationService: LocationService) => {
  try {
    const backendLocation = await locationService.find(BACKEND_CONNECTION)
    if (backendLocation === undefined) {
      errorMessage(
        `Backend Server connection ${BACKEND_CONNECTION.prefix.toJSON()} not available`
      )
    }
    return backendLocation
  } catch (e) {
    errorMessage('Failed to resolve backend Url', e)
    return
  }
}

// peas-exposure-service's own component prefix -- follows
// peas-procedure-setup-service's convention (APS.PeasExposureService), not
// peas-procedure-data-service's (a known prototype mistake to be corrected
// later). See PeasExposureServiceImpl.scala's ImageDisplayEventSourcePrefix
// for the same value used on the event-publishing side.
const exposureBackendServicePrefix = Prefix.fromString('APS.PeasExposureService')
export const EXPOSURE_BACKEND_CONNECTION = HttpConnection(
  exposureBackendServicePrefix,
  'Service'
)

export const getExposureBackendUrl = (
  locationService: LocationService
): Promise<string | undefined> =>
  getExposureBackendLocation(locationService).then((location) => location?.uri)

const getExposureBackendLocation = async (locationService: LocationService) => {
  try {
    const backendLocation = await locationService.find(EXPOSURE_BACKEND_CONNECTION)
    if (backendLocation === undefined) {
      errorMessage(
        `Exposure Service connection ${EXPOSURE_BACKEND_CONNECTION.prefix.toJSON()} not available`
      )
    }
    return backendLocation
  } catch (e) {
    errorMessage('Failed to resolve Exposure Service backend Url', e)
    return
  }
}
