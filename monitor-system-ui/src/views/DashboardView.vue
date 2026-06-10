<!--
  设备监控仪表盘
  使用 ECharts 展示主控区设备实时温度柱状图，数据来自后端分页接口。
-->
<script setup lang="ts">
import { getDevicePage } from '../api/device'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'

const chartRef = ref(null)
let myChart: echarts.ECharts | null = null

onMounted(async () => {
  myChart = echarts.init(chartRef.value)
  myChart.showLoading()

  try {
    const pageData = await getDevicePage(1, 10);
    const validData = pageData.records;

    myChart.setOption({
      title: { text: '主控区实时核心温度监控', left: 'center', textStyle: { color: '#606266' } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: validData.map((i: any) => i.deviceCode) },
      yAxis: { type: 'value', name: '温度 (℃)' },
      series: [{
        name: '核心温度',
        type: 'bar',
        data: validData.map((i: any) => i.temperature),
        itemStyle: { color: '#409EFF' },
        barWidth: '40%'
      }]
    })
  } catch (error) {
    ElMessage.error('连接监控服务器失败，请检查后端状态！')
    console.error(error)
  } finally {
    myChart.hideLoading()
  }

  window.addEventListener('resize', () => myChart?.resize())
})

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