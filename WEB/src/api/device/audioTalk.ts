import { defHttp } from '@/utils/http/axios';

const AUDIO_TALK_PREFIX = '/video/camera/audio/talk';

const commonApi = (
  method: 'get' | 'post',
  url: string,
  params: Record<string, unknown> = {},
  isTransformResponse = true,
) =>
  defHttp[method](
    {
      url,
      ...(method === 'get' ? { params } : { data: params }),
    },
    { isTransformResponse },
  );

export interface AudioTalkCapabilities {
  supported?: boolean;
  audio_backchannel_supported?: boolean;
  codecs?: string[];
  sample_rate?: number;
  channels?: number;
  onvif_supported?: boolean;
}

export const getAudioTalkCapabilities = (deviceId: string) =>
  commonApi('get', `${AUDIO_TALK_PREFIX}/capabilities`, { device_id: deviceId });

export const startOnvifAudioTalk = (payload: {
  device_id: string;
  audio_codec?: string;
  sample_rate?: number;
  volume_gain?: number;
  noise_suppression?: boolean;
  echo_cancellation?: boolean;
}) => commonApi('post', `${AUDIO_TALK_PREFIX}/start`, payload, false);

export const stopOnvifAudioTalk = (sessionId: string) =>
  commonApi('post', `${AUDIO_TALK_PREFIX}/stop`, { session_id: sessionId }, false);

export const sendOnvifAudioData = (sessionId: string, audioDataBase64: string) =>
  commonApi(
    'post',
    `${AUDIO_TALK_PREFIX}/send`,
    { session_id: sessionId, audio_data: audioDataBase64 },
    false,
  );
