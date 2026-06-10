<!--
  AI 智能排障对话页
  功能:
    - SSE 流式对话，逐 chunk 渲染 AI 回复
    - Markdown 实时渲染（marked）
    - Human-in-the-Loop (HITL) 审批面板：用户可确认或拒绝 AI 的操作请求
    - 降级模式通知展示
-->
<script setup lang="ts">
import { askAiStream } from '../api/ai'
import type { HitlPendingData } from '../api/ai'
import { ref, nextTick } from 'vue'
import { UserFilled, Opportunity } from '@element-plus/icons-vue'
import { marked } from 'marked'

interface Message {
  role: 'user' | 'assistant'
  content: string
  hitlPending?: HitlPendingData
  hitlResolved?: boolean
}

const inputMsg = ref('')
const loading = ref(false)
const currentConversationId = ref('')
const messages = ref<Message[]>([
  { role: 'assistant', content: '您好！我是 AI 智能排障专家。请描述您遇到的设备故障现象或输入故障码，我将为您检索内部知识库并提供方案。' }
])
const scrollRef = ref<any>(null)

const scrollToBottom = async () => {
  await nextTick()
  if (scrollRef.value) {
    scrollRef.value.setScrollTop(scrollRef.value.wrapRef.scrollHeight)
  }
}

const parseMarkdown = (text: string) => {
  if (!text) return ''
  return marked.parse(text) as string
}

const handleHitlApprove = (msgIndex: number, optionId?: string) => {
  const msg = messages.value[msgIndex]
  if (msg) {
    msg.hitlResolved = true
    const label = msg.hitlPending?.options?.find(o => o.id === optionId)?.label || '确认执行'
    inputMsg.value = `[HITL-APPROVE] ${label}`
    handleSend()
  }
}

const handleHitlReject = (msgIndex: number) => {
  const msg = messages.value[msgIndex]
  if (msg) {
    msg.hitlResolved = true
    inputMsg.value = '[HITL-REJECT] 拒绝执行该操作'
    handleSend()
  }
}

const handleSend = async () => {
  if (!inputMsg.value.trim()) return

  const userMsg = inputMsg.value
  messages.value.push({ role: 'user', content: userMsg })
  inputMsg.value = ''

  loading.value = true
  scrollToBottom()

  let assistantMsgIndex = -1

  try {
    await askAiStream(userMsg, {
      onConversationId: (id) => {
        currentConversationId.value = id
      },
      onMessage: (chunkText) => {
        if (loading.value) {
          loading.value = false
          assistantMsgIndex = messages.value.length
          messages.value.push({ role: 'assistant', content: chunkText })
        } else {
          const assistantMsg = messages.value[assistantMsgIndex]
          if (assistantMsg) {
            assistantMsg.content += chunkText
          } else {
            assistantMsgIndex = messages.value.length
            messages.value.push({ role: 'assistant', content: chunkText })
          }
        }
        scrollToBottom()
      },
      onHitlPending: (data: HitlPendingData) => {
        if (loading.value) {
          loading.value = false
        }
        assistantMsgIndex = messages.value.length
        messages.value.push({
          role: 'assistant',
          content: data.question || 'AI 请求执行一项操作，需要您的确认：',
          hitlPending: data,
          hitlResolved: false
        })
        scrollToBottom()
      },
      onError: (msg) => {
        if (loading.value) {
          loading.value = false
          messages.value.push({ role: 'assistant', content: `❌ ${msg}` })
        } else {
          const assistantMsg = messages.value[assistantMsgIndex]
          if (assistantMsg) {
            assistantMsg.content += `\n\n*[${msg}]*`
          }
        }
        scrollToBottom()
      },
      onFallbackNotice: (msg) => {
        const assistantMsg = messages.value[assistantMsgIndex]
        if (assistantMsg) {
          assistantMsg.content += `\n\n⚠️ *${msg}*`
        }
        scrollToBottom()
      }
    }, currentConversationId.value || undefined)
  } catch (error) {
    if (loading.value) {
      loading.value = false
      messages.value.push({ role: 'assistant', content: '❌ 抱歉，服务器开小差了或网络连接中断。' })
    } else {
      const assistantMsg = messages.value[assistantMsgIndex]
      if (assistantMsg) {
        assistantMsg.content += '\n\n*[网络连接中断]*'
      } else {
        messages.value.push({ role: 'assistant', content: '❌ 抱歉，服务器开小差了或网络连接中断。' })
      }
    }
    scrollToBottom()
  }
}
</script>

<template>
  <div class="chat-container">
    <el-scrollbar ref="scrollRef" class="chat-list">

      <div v-for="(msg, index) in messages" :key="index" :class="['msg-wrapper', msg.role]">
        <el-avatar :icon="msg.role === 'user' ? UserFilled : Opportunity"
                   :size="40"
                   :style="{ backgroundColor: msg.role === 'user' ? '#409EFF' : '#67C23A' }" />
        <div class="msg-content">
          <div class="role-name">{{ msg.role === 'user' ? '运维员' : 'AI 专家' }}</div>

          <div v-if="msg.role === 'user'" class="bubble">{{ msg.content }}</div>

          <div v-else class="bubble markdown-body" v-html="parseMarkdown(msg.content)"></div>

          <div v-if="msg.role === 'assistant' && msg.hitlPending && !msg.hitlResolved" class="hitl-panel">
            <div class="hitl-header">
              <el-icon color="#E6A23C" :size="16"><Opportunity /></el-icon>
              <span>人工确认 (HITL)</span>
            </div>
            <div v-if="msg.hitlPending.options && msg.hitlPending.options.length > 0" class="hitl-options">
              <el-button
                v-for="opt in msg.hitlPending.options"
                :key="opt.id"
                size="small"
                type="primary"
                plain
                @click="handleHitlApprove(index, opt.id)"
              >
                {{ opt.label }}
              </el-button>
            </div>
            <div v-else class="hitl-options">
              <el-button size="small" type="primary" @click="handleHitlApprove(index)">确认执行</el-button>
            </div>
            <el-button size="small" type="danger" plain @click="handleHitlReject(index)">拒绝</el-button>
          </div>

          <div v-if="msg.role === 'assistant' && msg.hitlPending && msg.hitlResolved" class="hitl-resolved">
            <el-tag size="small" type="info">已处理</el-tag>
          </div>
        </div>
      </div>

      <div v-if="loading" class="msg-wrapper assistant">
        <el-avatar :icon="Opportunity" :size="40" style="background-color: #67C23A" />
        <div class="msg-content">
          <div class="role-name">AI 专家</div>
          <div class="bubble loading-dots">
            <span class="dot">.</span><span class="dot">.</span><span class="dot">.</span> 正在检索核心知识库
          </div>
        </div>
      </div>

    </el-scrollbar>

    <div class="input-area">
      <el-input v-model="inputMsg"
                placeholder="请输入故障码（如 T-994）或故障现象..."
                @keyup.enter="handleSend"
                :disabled="loading">
        <template #append>
          <el-button type="primary" @click="handleSend" :loading="loading">发送</el-button>
        </template>
      </el-input>
    </div>
  </div>
</template>

<style scoped>
.chat-container {
  height: calc(100vh - 220px);
  display: flex;
  flex-direction: column;
}
.chat-list {
  flex: 1;
  padding: 20px;
  background-color: #f9f9f9;
  border-radius: 8px;
}
.msg-wrapper {
  display: flex;
  margin-bottom: 25px;
}
.msg-wrapper.user {
  flex-direction: row-reverse;
}
.msg-content {
  margin: 0 15px;
  max-width: 75%;
}
.role-name {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.user .role-name {
  text-align: right;
}
.bubble {
  padding: 12px 16px;
  border-radius: 8px;
  background-color: #fff;
  line-height: 1.6;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
  font-size: 14px;
}
.user .bubble {
  background-color: #ecf5ff;
  color: #409EFF;
  white-space: pre-wrap;
}
.assistant .bubble {
  background-color: #f0f9eb;
  color: #303133;
}

:deep(.markdown-body p) {
  margin: 0 0 10px 0;
}
:deep(.markdown-body p:last-child) {
  margin-bottom: 0;
}
:deep(.markdown-body strong) {
  color: #409EFF;
  font-weight: 600;
}
:deep(.markdown-body ol), :deep(.markdown-body ul) {
  margin: 0 0 10px 0;
  padding-left: 20px;
}
:deep(.markdown-body li) {
  margin-bottom: 5px;
}

.hitl-panel {
  margin-top: 8px;
  padding: 10px 14px;
  border: 1px solid #E6A23C;
  border-radius: 6px;
  background-color: #FDF6EC;
}
.hitl-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #E6A23C;
  margin-bottom: 8px;
}
.hitl-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.hitl-resolved {
  margin-top: 6px;
}

.loading-dots {
  color: #909399;
  font-style: italic;
}
.dot {
  animation: blink 1.4s infinite both;
  font-weight: bold;
}
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink {
  0% { opacity: 0.2; }
  20% { opacity: 1; }
  100% { opacity: 0.2; }
}
.input-area {
  margin-top: 20px;
}
</style>
