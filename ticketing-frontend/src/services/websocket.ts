import { Client, type IMessage } from '@stomp/stompjs'
import type { WebSocketSeatUpdate } from '../types'

export class StompWebSocketService {
  private client: Client | null = null
  private connected: boolean = false
  private onStatusChangeCallback?: (connected: boolean) => void
  private onSeatUpdateCallback?: (update: WebSocketSeatUpdate) => void

  public connect(
    eventId: string,
    onSeatUpdate: (update: WebSocketSeatUpdate) => void,
    onStatusChange?: (connected: boolean) => void
  ) {
    this.onSeatUpdateCallback = onSeatUpdate
    this.onStatusChangeCallback = onStatusChange

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const brokerURL = `${protocol}//${window.location.host}/ws`

    this.client = new Client({
      brokerURL,
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        this.connected = true
        if (this.onStatusChangeCallback) {
          this.onStatusChangeCallback(true)
        }
        console.log('[WebSocket] Connected to STOMP broker')

        // Subscribe vào kênh cập nhật trạng thái ghế của sự kiện
        this.client?.subscribe(`/topic/events/${eventId}/seats`, (message: IMessage) => {
          try {
            const data: WebSocketSeatUpdate = JSON.parse(message.body)
            if (this.onSeatUpdateCallback) {
              this.onSeatUpdateCallback(data)
            }
          } catch (e) {
            console.error('[WebSocket] Failed to parse message body:', e)
          }
        })
      },
      onDisconnect: () => {
        this.connected = false
        if (this.onStatusChangeCallback) {
          this.onStatusChangeCallback(false)
        }
        console.log('[WebSocket] Disconnected from STOMP broker')
      },
      onStompError: (frame) => {
        console.error('[WebSocket] STOMP error:', frame.headers['message'], frame.body)
      },
      onWebSocketError: (error) => {
        console.warn('[WebSocket] Transport error (will retry):', error)
      },
    })

    this.client.activate()
  }

  public disconnect() {
    if (this.client) {
      this.client.deactivate()
      this.client = null
      this.connected = false
      if (this.onStatusChangeCallback) {
        this.onStatusChangeCallback(false)
      }
    }
  }

  public isConnected(): boolean {
    return this.connected
  }
}

export const stompService = new StompWebSocketService()
