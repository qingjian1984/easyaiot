<script lang="ts" setup>
import { computed } from 'vue';
import { NODE_ROLE_MAP, NODE_STATUS_MAP, SETUP_COPY } from '../../utils/constants';

defineOptions({ name: 'NodeMetaBadge' });

const props = withDefaults(
  defineProps<{
    type: 'status' | 'role' | 'readiness';
    status?: string;
    role?: string;
    ready?: boolean;
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
  if (props.type === 'status') {
    const key = props.status || 'pending';
    return `node-meta-badge--status-${NODE_STATUS_MAP[key] ? key : 'pending'}`;
  }
  if (props.type === 'role') {
    const key = props.role || 'compute';
    return `node-meta-badge--role-${NODE_ROLE_MAP[key] ? key : 'compute'}`;
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
