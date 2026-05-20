/**
 * WVP 国标通道列表字段归一化（与 gb28181 通道页、点播接口一致）
 */
export function normalizeWvpChannelItem(
  item: Record<string, any>,
  parentSipDeviceId?: string,
): Record<string, any> {
  const sipDeviceId = String(
    parentSipDeviceId ||
      item.parentDeviceId ||
      item.parentId ||
      item.gbParentId ||
      item.deviceIdentification ||
      '',
  ).trim();

  const channelGbId = String(
    item.channelId ||
      item.deviceChannelId ||
      item.gbDeviceId ||
      item.gbId ||
      '',
  ).trim();

  // WVP 通道行里 deviceId 常为通道国标编码，不能与 SIP 设备号混淆
  let resolvedChannelId = channelGbId;
  if (!resolvedChannelId && item.deviceId && String(item.deviceId) !== sipDeviceId) {
    resolvedChannelId = String(item.deviceId).trim();
  }
  if (!resolvedChannelId && item.id != null && String(item.id) !== sipDeviceId) {
    resolvedChannelId = String(item.id).trim();
  }

  const name =
    item.name || item.channelName || item.deviceName || item.gbName || resolvedChannelId || '-';

  return {
    ...item,
    deviceIdentification: sipDeviceId,
    /** 点播父设备：SIP 设备国标编号 */
    deviceId: sipDeviceId,
    /** 点播通道：通道国标编号 */
    channelId: resolvedChannelId,
    gbDeviceId: resolvedChannelId || item.gbDeviceId,
    name,
    manufacturer: item.manufacturer ?? item.manufacture ?? '',
    manufacture: item.manufacture ?? item.manufacturer ?? '',
    ptzTypeText: item.ptzTypeText ?? item.ptzType ?? item.deviceType ?? '-',
    createdTime: item.createdTime ?? item.createTime,
    updatedTime: item.updatedTime ?? item.updateTime,
  };
}

/** 从通道记录解析点播参数 */
export function resolveGbChannelPlayIds(
  record: Record<string, any>,
  parentSipDeviceId: string,
): { sipDeviceId: string; channelId: string } | null {
  const sip = String(parentSipDeviceId || record.deviceIdentification || record.deviceId || '').trim();
  const normalized = normalizeWvpChannelItem(record, sip);
  const channelId = String(normalized.channelId || '').trim();
  if (!sip || !channelId || channelId === sip) {
    return null;
  }
  return { sipDeviceId: sip, channelId };
}
