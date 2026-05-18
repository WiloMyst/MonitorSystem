<script setup lang="ts">
import { getDevicePage } from '../api/device' // 导入接口方法
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'

// 获取图表容器的引用
const chartRef = ref(null)
let myChart: echarts.ECharts | null = null

onMounted(async () => {
  // 1. 初始化 ECharts 实例
  myChart = echarts.init(chartRef.value)
  
  // 细节1：请求数据前，显示加载动画（国企领导最喜欢的交互反馈）
  myChart.showLoading()

  try {
    // 默认请求第 1 页，取 10 条（如果你的大屏需要更多，这里传大点的值）
    const pageData = await getDevicePage(1, 10);
    // 真正的数组被包在了 records 里面！
    const validData = pageData.records;
    
    // 3. 注入数据并渲染图表
    myChart.setOption({
      title: { text: '主控区实时核心温度监控', left: 'center', textStyle: { color: '#606266' } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: validData.map((i: any) => i.deviceCode) },
      yAxis: { type: 'value', name: '温度 (℃)' },
      series: [{
        name: '核心温度',
        type: 'bar',
        data: validData.map((i: any) => i.temperature),
        itemStyle: { color: '#409EFF' }, // 这里的蓝色与 Element Plus 的主题蓝完美呼应
        barWidth: '40%'
      }]
    })
  } catch (error) {
    // 细节2：如果 Java 没启动或报错，弹出优雅的红色错误提示，而不是让页面白屏
    ElMessage.error('连接监控服务器失败，请检查后端状态！')
    console.error(error)
  } finally {
    // 隐藏加载动画
    myChart.hideLoading()
  }

  // 细节3：监听浏览器窗口变化，让图表自动缩放（企业级前端必考题！）
  window.addEventListener('resize', () => myChart?.resize())
})

// 组件销毁时，清理监听器，防止内存泄漏（C++选手对这个一定很亲切）
onBeforeUnmount(() => {
  window.removeEventListener('resize', () => myChart?.resize())
  myChart?.dispose()
})
</script>

<template>
  <div class="dashboard-container">
    <div ref="chartRef" style="width: 100%; height: 400px;"></div>
  </div>
</template>