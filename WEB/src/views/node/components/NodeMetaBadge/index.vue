<script lang="ts" setup>
import { computed } from 'vue';
import { NODE_ROLE_MAP, NODE_STATUS_MAP, NODE_TERM, SETUP_COPY, CEPH_MOUNT_LABELS } from '../../utils/constants';

defineOptions({ name: 'NodeMetaBadge' });

const props = withDefaults(
  defineProps<{
    type: 'status' | 'role' | 'readiness' | 'scope' | 'ceph';
    status?: string;
    role?: string;
    ready?: boolean;
    cephStatus?: 'ready' | 'not_ready' | 'unknown';
    size?: 'md' | 'lg';
  }>(),
  {
    status: 'pending',
    role: 'compute',
    ready: false,
    size: 'md',
  },
);

const badgeClass = computed(() => {
  if (props.type === 'ceph') {
    const key = props.cephStatus || 'unknown';
    if (key === 'ready') return 'node-meta-badge--ceph-ready';
    if (key === 'not_ready') return 'node-meta-badge--ceph-not-ready';
    return 'node-meta-badge--ceph-unknown';
  }
  if (props.type === 'status') {
    const key = props.status || 'pending';
    return `node-meta-badge--status-${NODE_STATUS_MAP[key] ? key : 'pending'}`;
  }
  if (props.type === 'role') {
    const key = props.role || 'compute';
    return `node-meta-badge--role-${NODE_ROLE_MAP[key] ? key : 'compute'}`;
  }
  if (props.type === 'scope') {
    return 'node-meta-badge--scope-control-plane';
  }
  return props.ready ? 'node-meta-badge--readiness-ready' : 'node-meta-badge--readiness-pending';
});

const label = computed(() => {
  if (props.type === 'status') {
    const key = props.status || 'pending';
    return NODE_STATUS_MAP[key]?.text || props.status || '-';
  }
  if (props.type === 'role') {
    return NODE_ROLE_MAP[props.role || ''] || props.role || '-';
  }
  if (props.type === 'scope') {
    return NODE_TERM.controlPlaneNode;
  }
  if (props.type === 'ceph') {
    const key = props.cephStatus || 'unknown';
    return CEPH_MOUNT_LABELS[key] || CEPH_MOUNT_LABELS.unknown;
  }
  return props.ready ? SETUP_COPY.readinessReady : SETUP_COPY.readinessPending;
});

const showDot = computed(() => props.type === 'status');
</script>

<template>
  <span class="node-meta-badge" :class="[badgeClass, size === 'lg' ? 'node-meta-badge--lg' : '']">
    <span v-if="showDot" class="node-meta-badge__dot" />
    {{ label }}
  </span>
</template>
