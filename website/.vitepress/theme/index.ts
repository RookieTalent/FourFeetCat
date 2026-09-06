import DefaultTheme from 'vitepress/theme'
import LayoutSlot from './LayoutSlot.vue'
import './styles.css'

export default {
  extends: DefaultTheme,
  Layout: LayoutSlot
}
