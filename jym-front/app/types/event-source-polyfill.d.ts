declare module 'event-source-polyfill' {
  export class EventSourcePolyfill extends EventSource {
    constructor(url: string, options?: {
      headers?: Record<string, string>
      withCredentials?: boolean
      heartbeatTimeout?: number
    })
  }
}
