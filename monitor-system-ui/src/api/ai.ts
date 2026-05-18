// 我们不需要经过 utils/request.ts 的 axios 拦截器了，直接用原生 fetch
export const askAiStream = async (question: string, onMessage: (chunk: string) => void) => {
  const token = localStorage.getItem('sa-token') || '';
  
  // 1. 发起原生 Fetch 请求
  const response = await fetch(import.meta.env.VITE_APP_BASE_API + '/ai/ask', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'satoken': token // 别忘了带上护照，否则会被 Sa-Token 拦截
    },
    body: JSON.stringify({ question })
  });

  if (!response.ok) {
    throw new Error('网络请求失败');
  }

  // 2. 获取响应体的读取器 (Reader)
  const reader = response.body?.getReader();
  if (!reader) return;

  const decoder = new TextDecoder('utf-8'); // 用于将字节流解码为字符串

  // 3. 循环读取流数据
  while (true) {
    // done: 是否读完；value: 本次读到的字节数组 (Uint8Array)
    const { done, value } = await reader.read();
    
    if (done) {
      break; // 服务器说：“我说完了，挂断！”
    }

    // 将字节解码成字符串
    const chunk = decoder.decode(value, { stream: true });
    
    // 【坑点处理】：Spring 的 text/event-stream 默认格式是 "data: 实际内容\n\n"
    // 我们需要用正则把 "data:" 和后面的空行剔除，只保留纯文本内容
    const cleanText = chunk.replace(/^data:/gm, '').replace(/\n\n/g, '');
    
    if (cleanText) {
      // 触发回调函数，把字传给组件页面
      onMessage(cleanText); 
    }
  }
}