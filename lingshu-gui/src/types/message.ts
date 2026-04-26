export type MessageType =
  | 'text'
  | 'image'
  | 'file'
  | 'voice'
  | 'video'
  | 'link'
  | 'quote'
  | 'system';

export type MessageStatus = 'sending' | 'sent' | 'delivered' | 'read' | 'failed';

export interface BaseMessage {
  id: string;
  type: MessageType;
  senderId: string;
  senderName: string;
  senderAvatar?: string;
  timestamp: Date;
  status: MessageStatus;
  isSelf: boolean;
  metadata?: any;
}

export interface TextMessage extends BaseMessage {
  type: 'text';
  content: string;
}

export interface ImageMessage extends BaseMessage {
  type: 'image';
  url: string;
  width: number;
  height: number;
  thumbnailUrl?: string;
}

export interface FileMessage extends BaseMessage {
  type: 'file';
  fileName: string;
  fileSize: number;
  fileType: string;
  url: string;
}

export interface VoiceMessage extends BaseMessage {
  type: 'voice';
  url: string;
  duration: number;
  waveform?: number[];
}

export interface VideoMessage extends BaseMessage {
  type: 'video';
  url: string;
  thumbnailUrl?: string;
  duration: number;
  width: number;
  height: number;
}

export interface LinkMessage extends BaseMessage {
  type: 'link';
  title: string;
  description: string;
  url: string;
  thumbnailUrl?: string;
}

export interface QuoteMessage extends BaseMessage {
  type: 'quote';
  quotedMessageId: string;
  quotedContent: string;
  quotedSenderName: string;
  content: string;
}

export interface SystemMessage extends BaseMessage {
  type: 'system';
  content: string;
}

export type AnyMessage =
  | TextMessage
  | ImageMessage
  | FileMessage
  | VoiceMessage
  | VideoMessage
  | LinkMessage
  | QuoteMessage
  | SystemMessage;

export function isTextMessage(msg: BaseMessage): msg is TextMessage {
  return msg.type === 'text';
}
