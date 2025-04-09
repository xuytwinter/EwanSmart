import { createApp } from 'vue'
import App from './App.vue'
import router from './route'

import './styles/main.css'
import 'uno.css'

const app = createApp(App)

setupStore(app)

app.use(router)
app.mount('#app')
