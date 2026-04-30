import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

let registered = false

function ensureRegistered() {
  if (registered) return
  use([
    BarChart,
    LineChart,
    PieChart,
    GridComponent,
    TooltipComponent,
    LegendComponent,
    CanvasRenderer,
  ])
  registered = true
}

export function initStatisticsChart(element: HTMLElement) {
  ensureRegistered()
  return init(element)
}
