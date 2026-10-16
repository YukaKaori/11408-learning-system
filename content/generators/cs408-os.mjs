/**
 * 408 操作系统 — computational families. Every answer is a simulation, every
 * distractor a mistake candidates make (the wrong algorithm, forgetting the
 * initial loads, the other metric), every 解析 the trace that produced it.
 */
import { rng, seedOf } from '../lib/random.mjs'
import { shuffleOptions } from '../lib/choice.mjs'
import { cs408, family, fmt, nn, Retry, table } from '../lib/common.mjs'

const ALG_NAME = { FIFO: '先进先出（FIFO）', LRU: '最近最久未使用（LRU）', OPT: '最佳（OPT）' }

// --- 页面置换 -------------------------------------------------------------------------

function replace(algorithm, refs, frames) {
  const memory = []
  let faults = 0
  const trace = []
  refs.forEach((page, i) => {
    let fault = false
    let victim = null
    if (memory.includes(page)) {
      if (algorithm === 'LRU') {
        memory.splice(memory.indexOf(page), 1)
        memory.push(page)
      }
    } else {
      fault = true
      faults++
      if (memory.length >= frames) {
        let index = 0
        if (algorithm === 'OPT') {
          let farthest = -1
          memory.forEach((resident, k) => {
            const next = refs.indexOf(resident, i + 1)
            const distance = next === -1 ? Number.POSITIVE_INFINITY : next
            if (distance > farthest) {
              farthest = distance
              index = k
            }
          })
        }
        victim = memory[index]
        memory.splice(index, 1)
      }
      memory.push(page)
    }
    trace.push({ page, memory: [...memory], fault, victim })
  })
  return { faults, trace }
}

function replacement() {
  const random = rng(seedOf('cs408-os-replace'))
  const plan = ['LRU', 'FIFO', 'OPT', 'LRU', 'FIFO', 'OPT', 'LRU', 'FIFO', 'OPT', 'LRU', 'FIFO', 'OPT', 'LRU', 'OPT']
  return family(random, plan.length, (r, index) => {
    const algorithm = plan[index - 1]
    const frames = r.pick([3, 3, 4])
    const pages = frames + r.int(2, 3)
    const refs = Array.from({ length: r.int(11, 13) }, () => r.int(0, pages - 1))
    for (let i = 1; i < refs.length; i++) if (refs[i] === refs[i - 1]) throw new Retry()
    const result = replace(algorithm, refs, frames)
    const others = ['FIFO', 'LRU', 'OPT'].filter((a) => a !== algorithm).map((a) => replace(a, refs, frames).faults)
    const askHits = r.chance(0.25)
    const correct = askHits ? refs.length - result.faults : result.faults
    const mistakes = askHits
      ? [refs.length - others[0], refs.length - others[1], result.faults, refs.length - result.faults + frames]
      : [...others, result.faults - frames, result.faults + 1]
    const distractors = [...new Set(mistakes.filter((v) => v !== correct && v >= 0))]
    if (distractors.length < 3) throw new Retry()
    const { options, answer } = shuffleOptions(r, `${correct}`, distractors.map(String))
    const order = algorithm === 'FIFO' ? '（先装入的在前）' : algorithm === 'LRU' ? '（最久未用的在前）' : ''
    const rows = result.trace.map((step, i) => [
      i + 1,
      step.page,
      step.memory.join(', '),
      step.fault ? '缺页' + (step.victim !== null ? `，换出 ${step.victim}` : '') : '命中',
    ])
    return {
      ...cs408(`g-cs408-os-replace-${nn(index)}`, ['cs408.os.memory.replacement'], frames === 4 ? 3 : 2),
      stem: `某进程分配到 ${frames} 个物理页框，初始均为空。若页面引用串为 ${refs.join(', ')}，采用${ALG_NAME[algorithm]}页面置换算法，则访问过程中${askHits ? '命中（不缺页）的次数' : '发生的缺页次数'}为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `按${ALG_NAME[algorithm]}逐次模拟：\n\n${table(['次序', '访问页', `内存中的页${order}`, '结果'], rows)}\n\n共缺页 ${result.faults} 次（含开头 ${frames} 次装入空页框）${askHits ? `，命中次数 = ${refs.length} − ${result.faults} = ${correct}` : `，命中 ${refs.length - result.faults} 次`}。\n\n易错点：页框未满时的装入同样是缺页；${algorithm === 'LRU' ? 'LRU 每次命中都要把该页移到“最近使用”的一端' : algorithm === 'OPT' ? 'OPT 换出的是“将来最长时间不再访问”的页，看的是引用串的后半段' : 'FIFO 只看装入先后，命中不改变顺序'}。同一引用串下 ${['FIFO', 'LRU', 'OPT'].map((a) => `${a} ${replace(a, refs, frames).faults} 次`).join('、')}。`,
    }
  })
}

// --- 处理机调度 -----------------------------------------------------------------------

function run(algorithm, procs, quantum = 2) {
  const n = procs.length
  const finish = new Array(n).fill(0)
  const gantt = []
  const push = (name, from, to) => {
    const last = gantt[gantt.length - 1]
    if (last && last.name === name && last.to === from) last.to = to
    else gantt.push({ name, from, to })
  }
  if (algorithm === 'SRTF') {
    const left = procs.map((p) => p.burst)
    let t = 0
    let done = 0
    while (done < n) {
      const ready = procs.map((p, i) => i).filter((i) => procs[i].arrival <= t && left[i] > 0)
      if (ready.length === 0) {
        t = Math.min(...procs.map((p, i) => (left[i] > 0 ? p.arrival : Infinity)))
        continue
      }
      ready.sort((a, b) => left[a] - left[b] || procs[a].arrival - procs[b].arrival || a - b)
      const i = ready[0]
      push(procs[i].name, t, t + 1)
      left[i]--
      t++
      if (left[i] === 0) {
        finish[i] = t
        done++
      }
    }
  } else if (algorithm === 'RR') {
    const left = procs.map((p) => p.burst)
    const queue = []
    let t = 0
    let next = 0
    const admit = (until) => {
      while (next < n && procs[next].arrival <= until) queue.push(next++)
    }
    admit(0)
    while (queue.length > 0 || next < n) {
      if (queue.length === 0) {
        t = procs[next].arrival
        admit(t)
      }
      const i = queue.shift()
      const slice = Math.min(quantum, left[i])
      push(procs[i].name, t, t + slice)
      t += slice
      left[i] -= slice
      admit(t) // arrivals up to now enter before the preempted process
      if (left[i] > 0) queue.push(i)
      else finish[i] = t
    }
  } else {
    let t = 0
    const pending = procs.map((p, i) => i)
    while (pending.length > 0) {
      let ready = pending.filter((i) => procs[i].arrival <= t)
      if (ready.length === 0) {
        t = Math.min(...pending.map((i) => procs[i].arrival))
        ready = pending.filter((i) => procs[i].arrival <= t)
      }
      let chosen
      if (algorithm === 'FCFS') chosen = ready.sort((a, b) => procs[a].arrival - procs[b].arrival || a - b)[0]
      else if (algorithm === 'SJF') chosen = ready.sort((a, b) => procs[a].burst - procs[b].burst || procs[a].arrival - procs[b].arrival || a - b)[0]
      else {
        const ratio = (i) => (t - procs[i].arrival + procs[i].burst) / procs[i].burst
        chosen = ready.sort((a, b) => ratio(b) - ratio(a) || a - b)[0]
        const best = ratio(chosen)
        if (ready.filter((i) => Math.abs(ratio(i) - best) < 1e-9).length > 1) throw new Retry()
      }
      push(procs[chosen].name, t, t + procs[chosen].burst)
      t += procs[chosen].burst
      finish[chosen] = t
      pending.splice(pending.indexOf(chosen), 1)
    }
  }
  const turnaround = procs.map((p, i) => finish[i] - p.arrival)
  const waiting = procs.map((p, i) => turnaround[i] - p.burst)
  const avg = (list) => list.reduce((a, b) => a + b, 0) / n
  return { finish, turnaround, waiting, avgTurnaround: avg(turnaround), avgWaiting: avg(waiting), gantt }
}

const SCHED_NAME = {
  FCFS: '先来先服务（FCFS）',
  SJF: '非抢占式短作业优先（SJF）',
  SRTF: '抢占式最短剩余时间优先（SRTF）',
  HRRN: '高响应比优先（HRRN，非抢占）',
  RR: '时间片轮转（RR，时间片为 2）',
}

function scheduling() {
  const random = rng(seedOf('cs408-os-scheduling'))
  const plan = ['SJF', 'RR', 'SRTF', 'FCFS', 'HRRN', 'SJF', 'RR', 'SRTF', 'HRRN', 'RR', 'SRTF', 'FCFS']
  return family(random, plan.length, (r, index) => {
    const algorithm = plan[index - 1]
    const n = r.pick([4, 4, 5])
    let arrival = 0
    const procs = Array.from({ length: n }, (_, i) => {
      if (i > 0) arrival += r.int(0, 3)
      return { name: `P${i + 1}`, arrival, burst: r.int(1, 7) }
    })
    const result = run(algorithm, procs)
    const metric = r.chance(0.6) ? 'turnaround' : 'waiting'
    const value = (res) => (metric === 'turnaround' ? res.avgTurnaround : res.avgWaiting)
    const correct = value(result)
    const mistakes = Object.keys(SCHED_NAME)
      .filter((a) => a !== algorithm)
      .map((a) => value(run(a, procs)))
    mistakes.push(metric === 'turnaround' ? result.avgWaiting : result.avgTurnaround)
    const distractors = [...new Set(mistakes.map((v) => fmt(v)))].filter((v) => v !== fmt(correct))
    if (distractors.length < 3) throw new Retry()
    const { options, answer } = shuffleOptions(r, fmt(correct), distractors)
    const rows = procs.map((p, i) => [p.name, p.arrival, p.burst, result.finish[i], result.turnaround[i], result.waiting[i]])
    const gantt = result.gantt.map((g) => `${g.from}–${g.to} ${g.name}`).join(' → ')
    const metricName = metric === 'turnaround' ? '平均周转时间' : '平均等待时间'
    return {
      ...cs408(`g-cs408-os-sched-${nn(index)}`, ['cs408.os.process.scheduling'], algorithm === 'RR' || algorithm === 'SRTF' || algorithm === 'HRRN' ? 3 : 2),
      stem: `某单处理机系统中有 ${n} 个进程，到达时间与运行时间如下表（时间单位相同，忽略调度开销${algorithm === 'RR' ? '；新到达的进程先于被剥夺的进程进入就绪队列' : ''}；条件相同时先到达者优先）。采用${SCHED_NAME[algorithm]}调度算法时，${metricName}为（ ）。\n\n${table(['进程', '到达时间', '运行时间'], procs.map((p) => [p.name, p.arrival, p.burst]))}`,
      type: 'single_choice',
      options,
      answer,
      analysis: `调度顺序（甘特图）：${gantt}。\n\n${table(['进程', '到达', '运行', '完成', '周转', '等待'], rows)}\n\n周转时间 = 完成时间 − 到达时间，等待时间 = 周转时间 − 运行时间。${metricName} = ${fmt(correct)}。\n\n易错点：${metric === 'turnaround' ? '不要把等待时间当成周转时间' : '等待时间不含运行时间'}；${algorithm === 'RR' ? '时间片轮转要按队列顺序逐片执行，注意同一时刻到达与被剥夺的入队先后' : algorithm === 'SRTF' ? '抢占式算法在每个新进程到达时都要比较剩余时间' : algorithm === 'HRRN' ? '响应比 = (等待时间 + 运行时间) / 运行时间，每次调度时重新计算' : '非抢占算法一旦选中就运行到结束'}。`,
    }
  })
}

// --- 磁盘调度 -------------------------------------------------------------------------

const DISK_NAME = {
  FCFS: '先来先服务（FCFS）',
  SSTF: '最短寻道时间优先（SSTF）',
  SCAN: '扫描算法（SCAN，到达磁盘端点后才折返）',
  LOOK: 'LOOK 算法（到达最远请求后即折返）',
}

function seek(algorithm, head, requests, up, maxCyl = 199) {
  let order = []
  if (algorithm === 'FCFS') order = [...requests]
  else if (algorithm === 'SSTF') {
    const left = [...requests]
    let at = head
    while (left.length > 0) {
      left.sort((a, b) => Math.abs(a - at) - Math.abs(b - at))
      if (left.length > 1 && Math.abs(left[0] - at) === Math.abs(left[1] - at)) throw new Retry()
      at = left.shift()
      order.push(at)
    }
  } else {
    const above = requests.filter((c) => c > head).sort((a, b) => a - b)
    const below = requests.filter((c) => c < head).sort((a, b) => b - a)
    order = up ? [...above, ...below] : [...below, ...above]
  }
  let distance = 0
  let at = head
  const path = [head]
  if (algorithm === 'SCAN') {
    const first = up ? requests.filter((c) => c > head) : requests.filter((c) => c < head)
    const second = up ? requests.filter((c) => c < head) : requests.filter((c) => c > head)
    if (first.length === 0 || second.length === 0) throw new Retry()
    const end = up ? maxCyl : 0
    for (const c of order.slice(0, first.length)) path.push(c)
    path.push(end)
    for (const c of order.slice(first.length)) path.push(c)
  } else {
    for (const c of order) path.push(c)
  }
  for (let i = 1; i < path.length; i++) distance += Math.abs(path[i] - path[i - 1])
  at = path[path.length - 1]
  return { distance, path, at }
}

function disk() {
  const random = rng(seedOf('cs408-os-disk'))
  const plan = ['SSTF', 'SCAN', 'LOOK', 'FCFS', 'SSTF', 'SCAN', 'LOOK', 'SSTF', 'SCAN', 'LOOK', 'FCFS', 'SSTF']
  return family(random, plan.length, (r, index) => {
    const algorithm = plan[index - 1]
    const head = r.int(50, 150)
    const requests = r.distinct(8, 0, 199).filter((c) => c !== head)
    if (requests.length < 7) throw new Retry()
    const up = r.chance(0.5)
    const result = seek(algorithm, head, requests, up)
    const others = Object.keys(DISK_NAME)
      .filter((a) => a !== algorithm)
      .map((a) => seek(a, head, requests, up).distance)
    const correct = result.distance
    const distractors = [...new Set(others.filter((v) => v !== correct))]
    if (distractors.length < 3) {
      distractors.push(correct + Math.abs(head - (up ? 199 : 0)), correct - 10)
    }
    const { options, answer } = shuffleOptions(r, `${correct}`, [...new Set(distractors.filter((v) => v !== correct && v > 0))].map(String))
    return {
      ...cs408(`g-cs408-os-disk-${nn(index)}`, ['cs408.os.io.disk'], algorithm === 'FCFS' ? 1 : 2),
      stem: `某磁盘有 200 个柱面（编号 0–199），磁头当前位于 ${head} 号柱面，${up ? '正向柱面号增大的方向' : '正向柱面号减小的方向'}移动。请求队列依次为 ${requests.join(', ')}。采用${DISK_NAME[algorithm]}时，磁头移动经过的柱面总数为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `磁头的移动路径为 ${result.path.join(' → ')}。\n\n移动总量 = 各段距离之和 = ${result.path.slice(1).map((c, i) => Math.abs(c - result.path[i])).join(' + ')} = ${correct}。\n\n对照：${Object.keys(DISK_NAME).map((a) => `${a} ${seek(a, head, requests, up).distance}`).join('，')}。易错点：SCAN 要先走到磁盘端点（${up ? 199 : 0}）再折返，LOOK 只走到最远的请求；SSTF 每次都从“当前位置”重新找最近的请求。`,
    }
  })
}

// --- 银行家算法 -------------------------------------------------------------------------

function safeOrder(need, alloc, available, order) {
  const work = [...available]
  const steps = []
  for (const i of order) {
    if (need[i].some((v, k) => v > work[k])) return { safe: false, failAt: i, work, steps }
    for (let k = 0; k < work.length; k++) work[k] += alloc[i][k]
    steps.push({ i, work: [...work] })
  }
  return { safe: true, steps }
}

function findSafe(need, alloc, available) {
  const n = need.length
  const done = new Array(n).fill(false)
  const work = [...available]
  const order = []
  for (let round = 0; round < n; round++) {
    const i = need.findIndex((row, k) => !done[k] && row.every((v, j) => v <= work[j]))
    if (i === -1) return null
    done[i] = true
    order.push(i)
    for (let k = 0; k < work.length; k++) work[k] += alloc[i][k]
  }
  return order
}

function banker() {
  const random = rng(seedOf('cs408-os-banker'))
  return family(random, 8, (r, index) => {
    const n = 5
    const max = Array.from({ length: n }, () => Array.from({ length: 3 }, () => r.int(1, 9)))
    const alloc = max.map((row) => row.map((v) => r.int(0, v)))
    const need = max.map((row, i) => row.map((v, k) => v - alloc[i][k]))
    const available = Array.from({ length: 3 }, () => r.int(1, 4))
    const safe = findSafe(need, alloc, available)
    if (!safe) throw new Retry()
    // Three orders that are not safe, each failing at a stated step.
    const wrong = new Set()
    let guard = 0
    while (wrong.size < 3 && guard++ < 500) {
      const order = r.shuffle([0, 1, 2, 3, 4])
      if (!safeOrder(need, alloc, available, order).safe) wrong.add(order.join(','))
    }
    if (wrong.size < 3) throw new Retry()
    const name = (order) => order.map((i) => `P${i}`).join(', ')
    const correct = `⟨${name(safe)}⟩`
    const wrongOrders = [...wrong].map((s) => s.split(',').map(Number))
    const { options, answer } = shuffleOptions(r, correct, wrongOrders.map((o) => `⟨${name(o)}⟩`))
    const rows = max.map((row, i) => [`P${i}`, row.join(' '), alloc[i].join(' '), need[i].join(' ')])
    const trace = safeOrder(need, alloc, available, safe).steps.map((s) => `P${s.i} 运行完毕，Work = (${s.work.join(', ')})`).join('；')
    const fails = wrongOrders
      .map((o) => {
        const check = safeOrder(need, alloc, available, o)
        return `⟨${name(o)}⟩ 在 P${check.failAt} 处受阻：Need(P${check.failAt}) = (${need[check.failAt].join(', ')})，而此时 Work = (${check.work.join(', ')})`
      })
      .join('；\n')
    return {
      ...cs408(`g-cs408-os-banker-${nn(index)}`, ['cs408.os.process.deadlock'], 3),
      stem: `某系统有 A、B、C 三类资源和 P0–P4 五个进程，T 时刻的资源分配情况如下表，系统可用资源向量 Available = (${available.join(', ')})。下列进程执行序列中，属于安全序列的是（ ）。\n\n${table(['进程', 'Max（A B C）', 'Allocation（A B C）'], max.map((row, i) => [`P${i}`, row.join(' '), alloc[i].join(' ')]))}`,
      type: 'single_choice',
      options,
      answer,
      analysis: `先求 Need = Max − Allocation：\n\n${table(['进程', 'Max', 'Allocation', 'Need'], rows)}\n\n从 Work = Available = (${available.join(', ')}) 出发验证 ${correct}：${trace}，所有进程都能完成，故为安全序列。\n\n其余选项：\n${fails}。\n\n方法：安全性检查每一步都只看“Need ≤ Work”，进程完成后把它的 Allocation 加回 Work。`,
    }
  })
}

function deadlockResources() {
  const random = rng(seedOf('cs408-os-deadlock-min'))
  return family(random, 6, (r, index) => {
    const n = r.int(3, 6)
    const k = r.int(2, 5)
    const inverse = index % 2 === 0
    if (!inverse) {
      const correct = n * (k - 1) + 1
      const { options, answer } = shuffleOptions(r, `${correct}`, [`${n * k}`, `${n * (k - 1)}`, `${n * k - 1}`, `${(n - 1) * k}`])
      return {
        ...cs408(`g-cs408-os-deadlock-min-${nn(index)}`, ['cs408.os.process.deadlock'], 2),
        stem: `某系统中有 ${n} 个并发进程，都需要使用同类资源，每个进程最多需要 ${k} 个，资源逐个申请、用完才释放。为保证系统一定不会因争夺该类资源而发生死锁，系统至少应配置该类资源（ ）个。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `最坏情况：每个进程都已得到 ${k - 1} 个、还差 1 个，共占 ${n} × ${k - 1} = ${n * (k - 1)} 个。此时只要再有 1 个资源，就能让某个进程得到全部资源运行结束并释放，其余进程随之可以完成。\n\n所以至少需要 ${n} × (${k} − 1) + 1 = ${correct} 个。\n\n易错点：${n * k} 是“每个进程同时满足”的数量，远多于避免死锁所需；${n * (k - 1)} 个恰好可能让所有进程都卡在差 1 个的状态。`,
      }
    }
    const m = n * (k - 1) + r.int(1, k - 1)
    const correct = Math.floor((m - 1) / (k - 1))
    const { options, answer } = shuffleOptions(r, `${correct}`, [`${correct + 1}`, `${Math.floor(m / k)}`, `${correct - 1}`, `${Math.floor(m / (k - 1))}`])
    return {
      ...cs408(`g-cs408-os-deadlock-min-${nn(index)}`, ['cs408.os.process.deadlock'], 2),
      stem: `某系统有 ${m} 台同类设备，每个进程最多需要 ${k} 台，设备逐台申请、用完才释放。为保证系统不会因此发生死锁，系统中最多允许（ ）个进程并发使用该类设备。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `设最多 x 个进程，需满足 x × (${k} − 1) + 1 ≤ ${m}，即 x ≤ (${m} − 1) / ${k - 1} = ${fmt((m - 1) / (k - 1))}，取整得 x = ${correct}。\n\n验证：${correct} 个进程各占 ${k - 1} 台共 ${correct * (k - 1)} 台，仍剩 ${m - correct * (k - 1)} 台，总能让一个进程完成；若是 ${correct + 1} 个进程，它们可能各占 ${k - 1} 台共 ${(correct + 1) * (k - 1)} 台，${(correct + 1) * (k - 1) >= m ? '设备被分完而谁都无法完成，发生死锁' : '仍可能出现死锁'}。`,
    }
  })
}

// --- 内存管理 -------------------------------------------------------------------------

function paging() {
  const random = rng(seedOf('cs408-os-paging'))
  return family(random, 10, (r, index) => {
    const pageKB = r.pick([1, 2, 4])
    const size = pageKB * 1024
    const frames = r.distinct(6, 2, 30)
    const page = r.int(1, 5)
    const offset = r.int(1, size - 1)
    const logical = page * size + offset
    const physical = frames[page] * size + offset
    const hex = index % 2 === 0
    const show = (v) => (hex ? `0x${v.toString(16).toUpperCase()}` : `${v}`)
    const mistakes = [logical, frames[page] * size, frames[page - 1] * size + offset, frames[page] * size + (offset % 1000)]
    const distractors = [...new Set(mistakes.filter((v) => v !== physical))].map(show)
    if (distractors.length < 3) throw new Retry()
    const { options, answer } = shuffleOptions(r, show(physical), distractors)
    const bits = Math.log2(size)
    return {
      ...cs408(`g-cs408-os-paging-${nn(index)}`, ['cs408.os.memory.paging'], 2),
      stem: `某分页存储系统的页面大小为 ${pageKB} KB，某进程的页表如下（页号从 0 开始）。逻辑地址 ${show(logical)} 对应的物理地址为（ ）。\n\n${table(['页号', ...frames.map((_, i) => i)], [['页框号', ...frames]])}`,
      type: 'single_choice',
      options,
      answer,
      analysis: `页面大小 ${pageKB} KB = ${size} B = 2^${bits} B，所以页内偏移占低 ${bits} 位。\n\n页号 = ${show(logical)} ÷ ${size} 取整 = ${page}，页内偏移 = ${show(logical)} mod ${size} = ${show(offset)}。\n\n查页表得页框号 ${frames[page]}，物理地址 = ${frames[page]} × ${size} + ${show(offset)} = ${show(physical)}。\n\n易错点：偏移量直接照搬到物理地址，只替换页号部分；页大小按 ${size} 而不是 ${pageKB * 1000} 计算。`,
    }
  })
}

function tlb() {
  const random = rng(seedOf('cs408-os-tlb'))
  return family(random, 6, (r, index) => {
    const t = r.pick([100, 200, 120, 150])
    const e = r.pick([5, 10, 20])
    const h = r.pick([80, 90, 95, 98])
    const correct = e + t + ((100 - h) / 100) * t
    const parallel = (h / 100) * (e + t) + ((100 - h) / 100) * (2 * t)
    const noTlbTime = (h / 100) * t + ((100 - h) / 100) * (2 * t)
    const mistakes = [parallel, noTlbTime, 2 * t, (h / 100) * (e + t) + ((100 - h) / 100) * (e + t)]
    const distractors = [...new Set(mistakes.map((v) => fmt(v, 1)))].filter((v) => v !== fmt(correct, 1))
    if (distractors.length < 3) throw new Retry()
    const { options, answer } = shuffleOptions(r, `${fmt(correct, 1)} ns`, distractors.map((v) => `${v} ns`))
    return {
      ...cs408(`g-cs408-os-tlb-${nn(index)}`, ['cs408.os.memory.paging', 'cs408.co.memory.virtual'], 2),
      stem: `某系统采用一级页表，页表存放在内存中。访问一次内存需 ${t} ns，查一次快表（TLB）需 ${e} ns。地址变换时先查快表，未命中再访问内存中的页表。若快表命中率为 ${h}%，则访问一个数据的平均时间为（ ）。（忽略缺页和其他开销）`,
      type: 'single_choice',
      options,
      answer,
      analysis: `命中：查快表 + 访问数据 = ${e} + ${t} = ${e + t} ns；\n未命中：查快表 + 查内存页表 + 访问数据 = ${e} + ${t} + ${t} = ${e + 2 * t} ns。\n\n平均时间 = ${h}% × ${e + t} + ${100 - h}% × ${e + 2 * t} = ${fmt(correct, 1)} ns。\n\n易错点：题目说“先查快表、未命中再查页表”，所以未命中时快表的 ${e} ns 也要计入；若快表与页表并行查找，答案会变成 ${fmt(parallel, 1)} ns。`,
    }
  })
}

function pageTableLevels() {
  const random = rng(seedOf('cs408-os-levels'))
  return family(random, 6, (r, index) => {
    const va = r.pick([32, 39, 48, 48])
    const pageKB = r.pick([4, 4, 8])
    const pte = r.pick([4, 8])
    const offsetBits = Math.log2(pageKB * 1024)
    const perLevel = Math.log2((pageKB * 1024) / pte)
    const levels = Math.ceil((va - offsetBits) / perLevel)
    const { options, answer } = shuffleOptions(r, `${levels}`, [`${levels - 1}`, `${levels + 1}`, `${levels + 2}`, `${Math.ceil((va - offsetBits) / (perLevel + 1))}`].filter((v) => Number(v) > 0))
    return {
      ...cs408(`g-cs408-os-levels-${nn(index)}`, ['cs408.os.memory.paging'], 3),
      stem: `某系统的虚拟地址为 ${va} 位，页面大小为 ${pageKB} KB，页表项大小为 ${pte} B。若要求每一级页表都恰好能装入一个页面，则至少需要采用（ ）级页表。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `页内偏移 = log₂(${pageKB} K) = ${offsetBits} 位，页号共 ${va} − ${offsetBits} = ${va - offsetBits} 位。\n\n一页可放 ${pageKB * 1024} / ${pte} = 2^${perLevel} 个页表项，所以每级页表最多解析 ${perLevel} 位页号。\n\n级数 = ⌈${va - offsetBits} / ${perLevel}⌉ = ${levels}。\n\n易错点：页表项大小决定每页能放多少项，不能直接用页面字节数；结果要向上取整。`,
    }
  })
}

function allocation() {
  const random = rng(seedOf('cs408-os-allocation'))
  const NAMES = { FF: '首次适应（First Fit）', BF: '最佳适应（Best Fit）', WF: '最坏适应（Worst Fit）' }
  const plan = ['FF', 'BF', 'WF', 'BF', 'FF', 'WF']
  const allocate = (algorithm, partitions, requests) => {
    const free = [...partitions]
    const log = []
    for (const req of requests) {
      const fits = free.map((size, i) => ({ size, i })).filter((p) => p.size >= req)
      if (fits.length === 0) {
        log.push(`${req} K 无法分配`)
        continue
      }
      let pick
      if (algorithm === 'FF') pick = fits[0]
      else if (algorithm === 'BF') pick = fits.sort((a, b) => a.size - b.size || a.i - b.i)[0]
      else pick = fits.sort((a, b) => b.size - a.size || a.i - b.i)[0]
      free[pick.i] -= req
      log.push(`${req} K → 第 ${pick.i + 1} 个分区（剩 ${free[pick.i]} K）`)
    }
    return { free, log }
  }
  return family(random, plan.length, (r, index) => {
    const algorithm = plan[index - 1]
    const partitions = Array.from({ length: 5 }, () => r.int(2, 14) * 50)
    const requests = Array.from({ length: 4 }, () => r.int(8, 45) * 10)
    const result = allocate(algorithm, partitions, requests)
    const correct = Math.max(...result.free)
    const others = Object.keys(NAMES).filter((a) => a !== algorithm).map((a) => Math.max(...allocate(a, partitions, requests).free))
    const distractors = [...new Set([...others, Math.max(...partitions), correct - 50, correct + 50].filter((v) => v !== correct && v > 0))]
    if (distractors.length < 3) throw new Retry()
    const { options, answer } = shuffleOptions(r, `${correct} K`, distractors.map((v) => `${v} K`))
    return {
      ...cs408(`g-cs408-os-allocation-${nn(index)}`, ['cs408.os.memory.basics'], 2),
      stem: `某系统采用可变分区分配，按地址递增排列的空闲分区大小依次为 ${partitions.map((p) => `${p} K`).join('、')}。现依次有 ${requests.map((q) => `${q} K`).join('、')} 四个分配请求，采用${NAMES[algorithm]}算法（分区分配后剩余部分仍留在原位置）。全部处理完后，最大的空闲分区为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `按${NAMES[algorithm]}逐个分配：${result.log.join('；')}。\n\n最终空闲分区为 ${result.free.map((v) => `${v} K`).join('、')}，最大的是 ${correct} K。\n\n对照：${Object.keys(NAMES).map((a) => `${a} 结束后最大 ${Math.max(...allocate(a, partitions, requests).free)} K`).join('，')}。${algorithm === 'FF' ? '首次适应从头找第一个够大的分区。' : algorithm === 'BF' ? '最佳适应找“够用的最小分区”，容易留下难以利用的小碎片。' : '最坏适应总是切最大的分区，会很快用掉大分区。'}`,
    }
  })
}

// --- 同步、文件 -------------------------------------------------------------------------

function semaphore() {
  const random = rng(seedOf('cs408-os-semaphore'))
  return family(random, 6, (r, index) => {
    const s0 = r.int(1, 4)
    const p = r.int(s0 + 1, s0 + 5)
    const v = r.int(0, p - s0 - 1)
    const value = s0 - p + v
    const blocked = Math.max(0, -value)
    const show = (val, b) => `${val}，${b}`
    const correct = show(value, blocked)
    const distractors = [show(-value, blocked), show(value, blocked + 1), show(value + 1, blocked), show(s0 - p, p - s0), show(value, 0)]
    const { options, answer } = shuffleOptions(r, correct, distractors.filter((d) => d !== correct))
    return {
      ...cs408(`g-cs408-os-semaphore-${nn(index)}`, ['cs408.os.process.sync'], 2),
      stem: `信号量 S 的初值为 ${s0}。若先后有 ${p} 个进程对 S 执行 P 操作，此后又有 ${v} 个 V 操作（每个 V 操作都在已有进程阻塞时执行${v === 0 ? '，本题没有 V 操作' : ''}），则此时 S 的值和因 S 而阻塞的进程数分别为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `每次 P 操作 S 减 1，减后 S < 0 则调用进程阻塞；每次 V 操作 S 加 1，加后 S ≤ 0 则唤醒一个阻塞进程。\n\nS = ${s0} − ${p} + ${v} = ${value}。S 为负时其绝对值就是阻塞队列中的进程数，所以阻塞 ${blocked} 个。\n\n易错点：S 的值不会被“截断”在 0；负值的绝对值才表示等待的进程数。`,
    }
  })
}

function fileSize() {
  const random = rng(seedOf('cs408-os-filesize'))
  const unit = (bytes) => {
    if (bytes % (1024 * 1024 * 1024) === 0) return `${bytes / (1024 * 1024 * 1024)} GB`
    if (bytes % (1024 * 1024) === 0) return `${bytes / (1024 * 1024)} MB`
    return `${bytes / 1024} KB`
  }
  return family(random, 8, (r, index) => {
    const block = r.pick([512, 1024, 4096])
    const addr = 4
    const direct = r.pick([10, 12])
    const n = block / addr
    const total = (direct + n + n * n) * block
    const mistakes = [(n + n * n) * block, (direct + n) * block, (direct + 2 * n + n * n) * block, (direct + n + n * n) * (block / 2)]
    const distractors = [...new Set(mistakes.filter((v) => v !== total))].map(unit)
    const { options, answer } = shuffleOptions(r, unit(total), distractors)
    return {
      ...cs408(`g-cs408-os-filesize-${nn(index)}`, ['cs408.os.file.structure', 'cs408.os.file.concepts'], 3),
      stem: `某文件系统采用混合索引分配，索引结点中有 ${direct} 个直接地址项、1 个一级间接地址项和 1 个二级间接地址项。磁盘块大小为 ${block} B，每个地址项占 ${addr} B。该文件系统能支持的单个文件的最大长度为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `一个磁盘块可存放 ${block} / ${addr} = ${n} 个地址项。\n\n- 直接地址：${direct} 块；\n- 一级间接：${n} 块；\n- 二级间接：${n} × ${n} = ${n * n} 块。\n\n最大长度 = (${direct} + ${n} + ${n * n}) × ${block} B = ${unit(total)}。\n\n易错点：每级间接块容纳的是“地址项”而不是数据，块数要用 块大小 / 地址项大小 计算；直接地址项也要计入。`,
    }
  })
}

function bitmap() {
  const random = rng(seedOf('cs408-os-bitmap'))
  return family(random, 4, (r, index) => {
    const diskGB = r.pick([16, 32, 64, 128])
    const blockKB = r.pick([1, 4, 8])
    const blocks = (diskGB * 1024 * 1024) / blockKB
    const bytes = blocks / 8
    const unit = (b) => (b >= 1024 * 1024 ? `${b / (1024 * 1024)} MB` : `${b / 1024} KB`)
    const { options, answer } = shuffleOptions(r, unit(bytes), [unit(blocks), unit(bytes / 8), unit(bytes * 4), unit(bytes * 2)].filter((v) => v !== unit(bytes)))
    return {
      ...cs408(`g-cs408-os-bitmap-${nn(index)}`, ['cs408.os.file.system'], 2),
      stem: `某磁盘容量为 ${diskGB} GB，磁盘块大小为 ${blockKB} KB，用位示图管理空闲块，每一位对应一个磁盘块。该位示图至少占用（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `磁盘块数 = ${diskGB} GB / ${blockKB} KB = ${blocks} 块，每块对应 1 位，共 ${blocks} 位 = ${blocks} / 8 B = ${unit(bytes)}。\n\n易错点：位示图是按“位”记录的，换算字节时要除以 8。`,
    }
  })
}

export default {
  id: 'gen-cs408-os',
  file: 'cs408/gen-cs408-os.json',
  pack: {
    pack: 'gen-cs408-os',
    title: '408 操作系统 · 计算专项（生成）',
    version: 1,
    description: '页面置换、处理机调度、磁盘调度、银行家算法、死锁资源数、分页地址变换、快表、多级页表、分区分配、信号量、混合索引与位示图——答案由模拟计算得出，解析给出完整过程。',
    generator: 'content/generators/cs408-os.mjs',
  },
  build() {
    return [
      ...replacement(),
      ...scheduling(),
      ...disk(),
      ...banker(),
      ...deadlockResources(),
      ...paging(),
      ...tlb(),
      ...pageTableLevels(),
      ...allocation(),
      ...semaphore(),
      ...fileSize(),
      ...bitmap(),
    ]
  },
}
