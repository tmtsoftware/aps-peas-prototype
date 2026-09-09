export interface HttpResponse<R> extends Response {
  parsedBody?: R
}

const http = async <Res>(
  request: RequestInfo,
  init?: RequestInit
): Promise<HttpResponse<Res>> => {
  const response: HttpResponse<Res> = await fetch(request, init)

  if (!response.ok) {
    throw new Error(
      `Failed to fetch data, status code = ${response.status} , reason = ${response.statusText}`
    )
  }

  try {
    response.parsedBody = await response.json()
  } catch (ex) {
    throw new Error(`Failed to parse response, reason: ${ex} `)
  }

  return response
}

export const get = <Res>(path: string): Promise<HttpResponse<Res>> => {
  const args: RequestInit = { method: 'GET' }
  return http<Res>(path, args)
}

// For endpoints that return a raw binary body (e.g. peas-exposure-service's
// GET /retrieveLowResImage, which returns image/png bytes per the ICD, not
// JSON). Deliberately separate from http<Res> above rather than trying to
// make that one JSON-or-blob -- keeps both simple.
export const getBlob = async (path: string): Promise<Blob> => {
  const response = await fetch(path, { method: 'GET' })
  if (!response.ok) {
    throw new Error(
      `Failed to fetch data, status code = ${response.status} , reason = ${response.statusText}`
    )
  }
  return response.blob()
}

export const post = <Req, Res>(
  path: string,
  body: Req,
  headers?: HeadersInit
): Promise<HttpResponse<Res>> => {
  const args: RequestInit = {
    method: 'POST',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json', ...headers }
  }
  return http<Res>(path, args)
}

export const put = <Req, Res>(
  path: string,
  body: Req,
  headers?: HeadersInit
): Promise<HttpResponse<Res>> => {
  const args: RequestInit = {
    method: 'PUT',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json', ...headers }
  }
  return http<Res>(path, args)
}
