/**
 * 408 计算机网络 — computational families: channel capacity, addressing,
 * error detection, sliding windows, the MAC layer, TCP and IP. Answers are
 * computed; distractors are the classic slips (the missing factor 2, the
 * classful mask, hosts without the two reserved addresses, max for min).
 */
import { rng, seedOf } from '../lib/random.mjs'
import { shuffleOptions } from '../lib/choice.mjs'
import { cs408, family, fmt, nn, Retry, table } from '../lib/common.mjs'

const log2 = (x) => Math.log2(x)

// --- 物理层：奈氏准则与香农定理 ---------------------------------------------------------

function capacity() {
  const random = rng(seedOf('cs408-cn-capacity'))
  return family(random, 9, (r, index) => {
    const W = r.pick([3, 4, 6, 8, 10])
    const kind = ['nyquist', 'shannon', 'both'][index % 3]
    if (kind === 'nyquist') {
      const phases = r.pick([2, 4, 8])
      const amps = r.pick([1, 2, 4])
      const V = phases * amps
      if (V < 4) throw new Retry()
      const correct = 2 * W * log2(V)
      const { options, answer } = shuffleOptions(r, `${correct} kb/s`, [`${W * log2(V)} kb/s`, `${2 * W * V} kb/s`, `${2 * W} kb/s`, `${4 * W * log2(V)} kb/s`])
      return {
        ...cs408(`g-cs408-cn-capacity-${nn(index)}`, ['cs408.cn.physical.theory', 'cs408.cn.physical.coding'], 2),
        stem: `某无噪声信道的带宽为 ${W} kHz，采用${amps > 1 ? ` ${phases} 种相位、每种相位 ${amps} 种振幅的 QAM 调制` : ` ${phases} 相位调制`}，则该信道的最大数据传输速率为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `码元状态数 V = ${phases} × ${amps} = ${V}，每个码元携带 log₂${V} = ${log2(V)} bit。\n\n奈氏准则：无噪声信道的最大码元速率为 2W = ${2 * W} k 波特，所以最大数据率 = 2W·log₂V = 2 × ${W} × ${log2(V)} = ${correct} kb/s。\n\n易错点：漏掉系数 2 会得到 ${W * log2(V)} kb/s；把状态数 V 直接当作每码元比特数会严重高估。`,
      }
    }
    const k = r.pick([5, 6, 7, 8, 10])
    const snr = 2 ** k - 1
    const shannon = W * k
    if (kind === 'shannon') {
      const { options, answer } = shuffleOptions(r, `${shannon} kb/s`, [`${2 * shannon} kb/s`, `${W * snr} kb/s`, `${fmt(W * Math.log10(snr + 1), 1)} kb/s`, `${2 * W * k * 2} kb/s`])
      return {
        ...cs408(`g-cs408-cn-capacity-${nn(index)}`, ['cs408.cn.physical.theory'], 2),
        stem: `某信道带宽为 ${W} kHz，信噪比 S/N = ${snr}（即 ${snr}:1），根据香农定理，该信道的极限信息传输速率为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `香农公式 C = W·log₂(1 + S/N) = ${W} × log₂(1 + ${snr}) = ${W} × log₂${snr + 1} = ${W} × ${k} = ${shannon} kb/s。\n\n易错点：香农公式里没有系数 2；S/N 是功率比，若题目给的是分贝值要先换算：S/N = 10^(dB/10)。`,
      }
    }
    const V = r.pick([4, 8, 16, 32])
    const nyquist = 2 * W * log2(V)
    const correct = Math.min(nyquist, shannon)
    if (nyquist === shannon) throw new Retry()
    const { options, answer } = shuffleOptions(r, `${correct} kb/s`, [`${Math.max(nyquist, shannon)} kb/s`, `${nyquist + shannon} kb/s`, `${W * log2(V)} kb/s`, `${2 * shannon} kb/s`])
    return {
      ...cs408(`g-cs408-cn-capacity-${nn(index)}`, ['cs408.cn.physical.theory'], 3),
      stem: `某信道带宽为 ${W} kHz，信噪比 S/N = ${snr}。若采用 ${V} 种不同状态的码元传输数据，则该信道可以达到的最大数据传输速率为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `两个上限都要满足：\n\n- 奈氏准则（码元速率受带宽限制）：2W·log₂V = 2 × ${W} × ${log2(V)} = ${nyquist} kb/s；\n- 香农定理（信噪比限制）：W·log₂(1 + S/N) = ${W} × ${k} = ${shannon} kb/s。\n\n实际可达的最大速率取二者中较小者：${correct} kb/s。\n\n易错点：只用其中一个公式，或取了较大值 ${Math.max(nyquist, shannon)} kb/s。`,
    }
  })
}

// --- 网络层：子网、聚合、最长前缀匹配 -------------------------------------------------

const ip = (n) => [24, 16, 8, 0].map((s) => (n >>> s) & 255).join('.')
const maskOf = (p) => (p === 0 ? 0 : (0xffffffff << (32 - p)) >>> 0)

function subnet() {
  const random = rng(seedOf('cs408-cn-subnet'))
  return family(random, 12, (r, index) => {
    const p = r.int(20, 29)
    const address = ((r.int(10, 223) << 24) | (r.int(0, 255) << 16) | (r.int(0, 255) << 8) | r.int(1, 254)) >>> 0
    const mask = maskOf(p)
    const network = (address & mask) >>> 0
    const broadcast = (network | (~mask >>> 0)) >>> 0
    const hosts = 2 ** (32 - p) - 2
    const kind = index % 4
    const classful = (address & 0xffffff00) >>> 0
    const block = 2 ** (32 - p)
    if (network === address || broadcast === address) throw new Retry()
    const base = cs408(`g-cs408-cn-subnet-${nn(index)}`, ['cs408.cn.network.subnet', 'cs408.cn.network.ipv4'], p >= 26 ? 2 : 3)
    const steps = `前缀 /${p} 对应子网掩码 ${ip(mask)}，主机号占 ${32 - p} 位，每个子网 ${block} 个地址。\n\n网络地址 = ${ip(address)} AND ${ip(mask)} = ${ip(network)}；广播地址 = 网络地址的主机位全置 1 = ${ip(broadcast)}；可分配主机地址 2^${32 - p} − 2 = ${hosts} 个（${ip(network + 1)} ~ ${ip(broadcast - 1)}）。`
    if (kind === 0) {
      const { options, answer } = shuffleOptions(r, ip(network), [ip(classful), ip((network + block) >>> 0), ip(broadcast), ip((network - block) >>> 0)])
      return { ...base, stem: `IP 地址 ${ip(address)}/${p} 所在子网的网络地址是（ ）。`, type: 'single_choice', options, answer, analysis: `${steps}\n\n易错点：不要按传统分类（如 C 类 /24）直接把最后一段清零。` }
    }
    if (kind === 1) {
      const { options, answer } = shuffleOptions(r, ip(broadcast), [ip((classful | 255) >>> 0), ip((broadcast - 1) >>> 0), ip(network), ip((broadcast + block) >>> 0)])
      return { ...base, stem: `某主机的 IP 地址为 ${ip(address)}，子网掩码为 ${ip(mask)}。该主机所在子网的广播地址是（ ）。`, type: 'single_choice', options, answer, analysis: `${steps}\n\n易错点：广播地址是“本子网”主机位全 1，而不是 x.x.x.255。` }
    }
    if (kind === 2) {
      const { options, answer } = shuffleOptions(r, `${hosts}`, [`${hosts + 2}`, `${hosts + 1}`, `${2 ** (32 - p - 1) - 2}`, `${2 ** (32 - p + 1) - 2}`])
      return { ...base, stem: `网络 ${ip(network)}/${p} 中可分配给主机的 IP 地址最多有（ ）个。`, type: 'single_choice', options, answer, analysis: `${steps}\n\n易错点：主机号全 0（网络地址）和全 1（广播地址）不能分配给主机，所以要减 2。` }
    }
    // Which address shares the subnet?
    const same = (network + r.int(1, block - 2)) >>> 0
    if (same === address) throw new Retry()
    const near = [(network - 1) >>> 0, (broadcast + 1) >>> 0, (network + block + r.int(1, block - 2)) >>> 0, (network - block + 1) >>> 0]
    const { options, answer } = shuffleOptions(r, ip(same), near.map(ip))
    return { ...base, stem: `主机 A 的 IP 地址为 ${ip(address)}/${p}。下列地址中，与主机 A 处于同一子网、可以直接分配给主机的是（ ）。`, type: 'single_choice', options, answer, analysis: `${steps}\n\n只有 ${ip(same)} 落在 ${ip(network + 1)} ~ ${ip(broadcast - 1)} 之间；其余选项或是相邻子网的地址，或是网络地址、广播地址之外的边界。` }
  })
}

function aggregate() {
  const random = rng(seedOf('cs408-cn-aggregate'))
  return family(random, 5, (r, index) => {
    const p = r.int(20, 24)
    const q = p - 2
    const base = ((((r.int(10, 223) << 24) | (r.int(0, 255) << 16) | (r.int(0, 255) << 8)) >>> 0) & maskOf(q)) >>> 0
    const nets = [0, 1, 2, 3].map((i) => (base + i * 2 ** (32 - p)) >>> 0)
    const correct = `${ip(base)}/${q}`
    const { options, answer } = shuffleOptions(r, correct, [`${ip(base)}/${p}`, `${ip(base)}/${q - 1}`, `${ip((base & maskOf(q - 2)) >>> 0)}/${q - 2}`, `${ip(nets[3])}/${q}`])
    return {
      ...cs408(`g-cs408-cn-aggregate-${nn(index)}`, ['cs408.cn.network.subnet'], 3),
      stem: `路由器 R 的路由表中有以下 4 条到相邻网络的路由，它们的下一跳相同：${nets.map((n) => `${ip(n)}/${p}`).join('、')}。若将这 4 条路由聚合成一条，则聚合后的目的网络应为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `4 个网络的前 ${q} 位完全相同（第 ${q + 1}、${q + 2} 位依次取 00、01、10、11），所以聚合前缀长度为 ${p} − 2 = ${q}，网络地址取共同前缀后补 0：${correct}。\n\n易错点：聚合要找“最长公共前缀”，前缀太短（如 /${q - 1}）会把不属于这 4 个网络的地址也包含进来。`,
    }
  })
}

function longestPrefix() {
  const random = rng(seedOf('cs408-cn-lpm'))
  return family(random, 6, (r, index) => {
    const base = ((r.int(10, 200) << 24) | (r.int(0, 255) << 16)) >>> 0
    const p1 = r.int(16, 18)
    const p2 = r.int(p1 + 2, 22)
    const p3 = r.int(p2 + 2, 26)
    const dst = (base | (r.int(0, 255) << 8) | r.int(1, 254)) >>> 0
    const ports = r.shuffle(['S1', 'S2', 'S3'])
    const routes = [
      { net: (dst & maskOf(p1)) >>> 0, p: p1, port: ports[0] },
      { net: (dst & maskOf(p2)) >>> 0, p: p2, port: ports[1] },
      { net: ((dst & maskOf(p3)) ^ (1 << (32 - p3))) >>> 0, p: p3, port: ports[2] }, // a longer prefix that does not match
      { net: 0, p: 0, port: 'S0（默认路由）' },
    ]
    const matches = routes.filter((route) => (((dst & maskOf(route.p)) >>> 0) === route.net))
    const best = [...matches].sort((a, b) => b.p - a.p)[0]
    if (best.p !== p2) throw new Retry()
    const shuffled = r.shuffle(routes)
    const { options, answer } = shuffleOptions(r, best.port, routes.map((route) => route.port).filter((port) => port !== best.port))
    return {
      ...cs408(`g-cs408-cn-lpm-${nn(index)}`, ['cs408.cn.network.router', 'cs408.cn.network.subnet'], 3),
      stem: `路由器的转发表如下。收到目的地址为 ${ip(dst)} 的 IP 分组时，路由器将其从（ ）接口转发出去。\n\n${table(['目的网络', '接口'], shuffled.map((route) => [route.p === 0 ? '0.0.0.0/0' : `${ip(route.net)}/${route.p}`, route.port]))}`,
      type: 'single_choice',
      options,
      answer,
      analysis: `逐条检查目的地址是否落在各网络中：${routes.map((route) => `${route.p === 0 ? '0.0.0.0/0' : `${ip(route.net)}/${route.p}`} ${(((dst & maskOf(route.p)) >>> 0) === route.net) ? '匹配' : '不匹配'}`).join('；')}。\n\n多条匹配时选前缀最长的一条（最长前缀匹配），即 /${best.p}，从 ${best.port} 转发。\n\n易错点：${p3} 位前缀虽然最长，但 ${ip(dst)} 与它的网络号不同；默认路由只在没有其他匹配时使用。`,
    }
  })
}

// --- 数据链路层 -----------------------------------------------------------------------

const POLYS = [
  { bits: '1011', tex: 'x^3+x+1' },
  { bits: '1101', tex: 'x^3+x^2+1' },
  { bits: '10011', tex: 'x^4+x+1' },
  { bits: '11001', tex: 'x^4+x^3+1' },
]

function mod2(dividend, divisor) {
  const bits = dividend.split('')
  for (let i = 0; i + divisor.length <= bits.length; i++) {
    if (bits[i] === '1') {
      for (let j = 0; j < divisor.length; j++) bits[i + j] = bits[i + j] === divisor[j] ? '0' : '1'
    }
  }
  return bits.slice(bits.length - (divisor.length - 1)).join('')
}

function crc() {
  const random = rng(seedOf('cs408-cn-crc'))
  return family(random, 6, (r, index) => {
    const poly = POLYS[(index - 1) % POLYS.length]
    const k = poly.bits.length - 1
    const data = '1' + Array.from({ length: r.int(7, 9) }, () => (r.chance(0.5) ? '1' : '0')).join('')
    const remainder = mod2(data + '0'.repeat(k), poly.bits)
    const noShift = mod2(data, poly.bits)
    const flipped = remainder.split('').map((b) => (b === '1' ? '0' : '1')).join('')
    const { options, answer } = shuffleOptions(r, remainder, [noShift, flipped, data.slice(-k), remainder.split('').reverse().join('')])
    return {
      ...cs408(`g-cs408-cn-crc-${nn(index)}`, ['cs408.cn.link.framing'], 2),
      stem: `要发送的数据为 ${data}，采用 CRC 校验，生成多项式为 $G(x)=${poly.tex}$（对应比特串 ${poly.bits}）。则应添加在数据后面的 CRC 校验码为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `生成多项式最高次为 ${k}，先在数据后补 ${k} 个 0 得 ${data}${'0'.repeat(k)}，再用 ${poly.bits} 做模 2 除法（异或，不借位），余数即校验码：${remainder}。\n\n发送的帧为 ${data}${remainder}，接收方用同一多项式去除，余数为 0 则认为无差错。\n\n易错点：忘记先补 ${k} 个 0（得到 ${noShift}）；模 2 除法每一步是异或，不是普通减法。`,
    }
  })
}

function windows() {
  const random = rng(seedOf('cs408-cn-arq'))
  return family(random, 8, (r, index) => {
    const kind = index % 3
    if (kind === 0) {
      const n = r.int(3, 6)
      const gbn = index % 2 === 0
      const correct = gbn ? 2 ** n - 1 : 2 ** (n - 1)
      const { options, answer } = shuffleOptions(r, `${correct}`, [`${2 ** n}`, gbn ? `${2 ** (n - 1)}` : `${2 ** n - 1}`, `${2 ** (n - 1) - 1}`, `${n}`])
      return {
        ...cs408(`g-cs408-cn-arq-${nn(index)}`, ['cs408.cn.link.arq'], 2),
        stem: `数据链路层采用${gbn ? '后退 N 帧（GBN）' : '选择重传（SR）'}协议，帧序号用 ${n} 位编号${gbn ? '' : '，接收窗口与发送窗口大小相同'}。为保证协议正确工作，发送窗口的最大尺寸为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: gbn
          ? `GBN 的接收窗口为 1，发送窗口 W_T 需满足 W_T ≤ 2^n − 1 = 2^${n} − 1 = ${correct}。若取 2^${n} = ${2 ** n}，全部确认丢失时接收方无法区分新帧与重传帧。`
          : `SR 中 W_T + W_R ≤ 2^n，且 W_R = W_T，所以 W_T ≤ 2^(n−1) = 2^${n - 1} = ${correct}。若窗口更大，新旧帧的序号会在接收窗口中重叠。`,
      }
    }
    if (kind === 1) {
      // Parameters derived from a clean utilization, so the answer is exact.
      const prop = r.pick([10, 15, 20, 25, 30])
      const u = r.pick([0.2, 0.25, 0.4, 0.5])
      const tx = (2 * prop * u) / (1 - u)
      const rate = r.pick([1, 2, 4])
      const bits = Math.round(tx * rate * 1000)
      if (Math.abs(bits - tx * rate * 1000) > 1e-6) throw new Retry()
      const pct = (x) => `${fmt(x * 100, 1)}%`
      const { options, answer } = shuffleOptions(r, pct(u), [pct(tx / (tx + prop)), pct(Math.min(1, tx / (2 * prop))), pct(Math.min(1, (2 * tx) / (tx + 2 * prop))), pct(1 - u)])
      return {
        ...cs408(`g-cs408-cn-arq-${nn(index)}`, ['cs408.cn.link.arq'], 3),
        stem: `两站点间采用停止-等待协议，数据传输速率为 ${rate} Mb/s，单向传播时延为 ${prop} ms，数据帧长为 ${bits} bit。忽略确认帧的发送时间和处理时间，信道利用率为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `发送一帧用时 T = ${bits} bit ÷ ${rate} Mb/s = ${fmt(tx, 3)} ms；发出后要等确认返回，一个周期为 T + 2 × ${prop} = ${fmt(tx + 2 * prop, 3)} ms。

利用率 U = T / (T + RTT) = ${fmt(tx, 3)} / ${fmt(tx + 2 * prop, 3)} = ${pct(u)}。

易错点：往返时延是单向时延的 2 倍。`,
      }
    }
    const frameMs = r.pick([1, 2, 4])
    const prop = r.pick([5, 10, 15, 20, 25])
    const need = Math.ceil((frameMs + 2 * prop) / frameMs)
    const n = Math.ceil(Math.log2(need + 1))
    const { options, answer } = shuffleOptions(r, `${n}`, [`${n - 1}`, `${n + 1}`, `${Math.ceil(Math.log2(need))}` === `${n}` ? `${n + 2}` : `${Math.ceil(Math.log2(need))}`, `${need}`])
    return {
      ...cs408(`g-cs408-cn-arq-${nn(index)}`, ['cs408.cn.link.arq'], 3),
      stem: `某链路发送一帧需要 ${frameMs} ms，单向传播时延为 ${prop} ms，采用后退 N 帧（GBN）协议且确认帧发送时间忽略不计。若要使发送方能连续不停地发送，帧序号至少需要（ ）位。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `从发出第一帧到收到它的确认需 ${frameMs} + 2 × ${prop} = ${frameMs + 2 * prop} ms，这段时间内能发出 ⌈${frameMs + 2 * prop} / ${frameMs}⌉ = ${need} 帧，发送窗口至少为 ${need}。\n\nGBN 要求 W_T ≤ 2^n − 1，即 2^n ≥ ${need + 1}，n 至少为 ${n} 位。`,
    }
  })
}

function csmacd() {
  const random = rng(seedOf('cs408-cn-csmacd'))
  return family(random, 5, (r, index) => {
    const rateMb = r.pick([10, 100, 1000])
    const meters = r.pick([500, 1000, 2000, 4000])
    const v = 2e8
    const tauUs = (meters / v) * 1e6
    const minBits = 2 * tauUs * rateMb
    if (!Number.isInteger(minBits)) throw new Retry()
    const minBytes = minBits / 8
    const show = (bits) => (Number.isInteger(bits / 8) ? `${bits / 8} B` : `${bits} bit`)
    const { options, answer } = shuffleOptions(r, show(minBits), [show(minBits / 2), show(minBits * 2), `${minBits} B`, show(minBits + 64 * 8)])
    return {
      ...cs408(`g-cs408-cn-csmacd-${nn(index)}`, ['cs408.cn.link.mac'], 2),
      stem: `某 CSMA/CD 总线网络的数据传输速率为 ${rateMb >= 1000 ? `${rateMb / 1000} Gb/s` : `${rateMb} Mb/s`}，两站点间的最远距离为 ${meters} m，信号传播速度为 $2\\times10^8$ m/s。为保证能检测到冲突，该网络的最短帧长为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `单程传播时延 τ = ${meters} m ÷ (2×10⁸ m/s) = ${fmt(tauUs, 3)} μs，争用期为 2τ = ${fmt(2 * tauUs, 3)} μs。\n\n站点必须在发完帧之前检测到冲突，所以帧的发送时间不小于 2τ：最短帧长 = 2τ × 速率 = ${fmt(2 * tauUs, 3)} μs × ${rateMb} Mb/s = ${minBits} bit${Number.isInteger(minBytes) ? ` = ${minBytes} B` : ''}。\n\n易错点：用的是往返时间 2τ 而不是 τ。`,
    }
  })
}

// --- 传输层：TCP ------------------------------------------------------------------------

function congestion() {
  const random = rng(seedOf('cs408-cn-congestion'))
  return family(random, 9, (r, index) => {
    const ssthresh0 = r.pick([8, 12, 16, 16, 24, 32])
    const event = index % 2 === 0 ? 'timeout' : 'dup'
    // Grow until a round with cwnd ≥ 8, where the event happens at its end.
    const rounds = []
    let cwnd = 1
    let ssthresh = ssthresh0
    const eventRound = r.int(5, 8)
    for (let round = 1; round <= eventRound; round++) {
      rounds.push({ round, cwnd, ssthresh })
      cwnd = cwnd < ssthresh ? Math.min(cwnd * 2, ssthresh) : cwnd + 1
    }
    const at = rounds[rounds.length - 1].cwnd
    if (at < 6) throw new Retry()
    ssthresh = Math.floor(at / 2)
    cwnd = event === 'timeout' ? 1 : ssthresh
    const after = r.int(2, 5)
    for (let i = 1; i <= after; i++) {
      rounds.push({ round: eventRound + i, cwnd, ssthresh })
      cwnd = cwnd < ssthresh ? Math.min(cwnd * 2, ssthresh) : cwnd + 1
    }
    const target = rounds[rounds.length - 1]
    const correct = target.cwnd
    const altEvent = event === 'timeout' ? ssthresh + (after - 1) : Math.min(2 ** (after - 1), ssthresh)
    const { options, answer } = shuffleOptions(r, `${correct}`, [`${altEvent}`, `${correct + 1}`, `${Math.max(1, correct - 1)}`, `${at + after}`, `${correct * 2}`])
    return {
      ...cs408(`g-cs408-cn-congestion-${nn(index)}`, ['cs408.cn.transport.tcp-congestion'], 3),
      stem: `TCP 连接的初始拥塞窗口 cwnd = 1（单位：MSS），慢开始门限 ssthresh = ${ssthresh0}。慢开始阶段每经过一个 RTT，cwnd 翻倍但不超过 ssthresh；cwnd 达到 ssthresh 后进入拥塞避免，每个 RTT 加 1。若第 ${eventRound} 个 RTT 的数据发出后${event === 'timeout' ? '发生超时' : '收到 3 个重复确认（执行快重传和快恢复）'}，则第 ${target.round} 个 RTT 开始时 cwnd 为（ ）。（第 1 个 RTT 开始时 cwnd = 1）`,
      type: 'single_choice',
      options,
      answer,
      analysis: `逐个 RTT 列出 cwnd：\n\n${table(['RTT', 'cwnd', 'ssthresh'], rounds.map((x) => [x.round, x.cwnd, x.ssthresh]))}\n\n第 ${eventRound} 个 RTT 的 cwnd = ${at}，${event === 'timeout' ? `超时后 ssthresh = ${at}/2 = ${ssthresh}，cwnd 重置为 1，重新慢开始` : `收到 3 个重复确认后 ssthresh = ${at}/2 = ${ssthresh}，快恢复令 cwnd = ssthresh = ${ssthresh}，随后直接拥塞避免`}。所以第 ${target.round} 个 RTT 开始时 cwnd = ${correct}。\n\n易错点：超时与快恢复的处理不同——超时 cwnd 回到 1，快恢复 cwnd 从新的 ssthresh 开始线性增长。`,
    }
  })
}

function tcpNumbers() {
  const random = rng(seedOf('cs408-cn-tcp-numbers'))
  return family(random, 6, (r, index) => {
    const x = r.int(100, 9000)
    const len1 = r.int(2, 30) * 100
    const len2 = r.int(2, 30) * 100
    const kind = index % 3
    if (kind === 0) {
      const correct = x + len1
      const { options, answer } = shuffleOptions(r, `${correct}`, [`${correct - 1}`, `${correct + 1}`, `${x}`, `${x + len1 + len2}`])
      return {
        ...cs408(`g-cs408-cn-tcp-numbers-${nn(index)}`, ['cs408.cn.transport.tcp-connection', 'cs408.cn.transport.tcp-reliable'], 2),
        stem: `主机甲向主机乙发送一个 TCP 报文段，序号为 ${x}，携带 ${len1} 字节数据。乙正确收到后，发回的确认号应为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `该段数据占用序号 ${x} ~ ${x + len1 - 1}。确认号表示“期望收到的下一个字节的序号”，即 ${x} + ${len1} = ${correct}。\n\n易错点：确认号不是收到的最后一个字节的序号（${correct - 1}）。`,
      }
    }
    if (kind === 1) {
      const correct = x
      const { options, answer } = shuffleOptions(r, `${correct}`, [`${x + len1}`, `${x + len1 + len2}`, `${x + len2}`, `${x - 1}`])
      return {
        ...cs408(`g-cs408-cn-tcp-numbers-${nn(index)}`, ['cs408.cn.transport.tcp-reliable'], 2),
        stem: `主机甲连续向主机乙发送两个 TCP 报文段，序号分别为 ${x} 和 ${x + len1}，分别携带 ${len1} 字节和 ${len2} 字节数据。若第一个段丢失、第二个段正确到达，乙收到第二个段后发回的确认号是（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `TCP 采用累积确认：确认号是“按序收到的下一个字节”。第一个段（${x} 起）丢失，乙按序期望的仍是序号 ${x}，所以确认号为 ${x}；第二个段会被暂存（失序缓存），不会让确认号越过缺口。`,
      }
    }
    const y = r.int(100, 9000)
    const correct = `${y}，${x + 1}`
    const { options, answer } = shuffleOptions(r, correct, [`${x + 1}，${y}`, `${y + 1}，${x + 1}`, `${y}，${x}`, `${y + 1}，${x}`])
    return {
      ...cs408(`g-cs408-cn-tcp-numbers-${nn(index)}`, ['cs408.cn.transport.tcp-connection'], 2),
      stem: `主机甲向主机乙发起 TCP 连接，第一次握手的 SYN 报文段序号 seq = ${x}。乙同意连接，并选择自己的初始序号为 ${y}。乙发回的 SYN+ACK 报文段中，序号和确认号分别为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `SYN 报文段不携带数据，但要消耗一个序号。乙的 SYN+ACK 用自己的初始序号 seq = ${y}，确认号 ack = ${x} + 1 = ${x + 1}。第三次握手时甲的确认号则为 ${y + 1}。`,
    }
  })
}

// --- 网络层：分片；时延 -------------------------------------------------------------------

function fragmentation() {
  const random = rng(seedOf('cs408-cn-fragment'))
  return family(random, 6, (r, index) => {
    const total = r.int(30, 60) * 100 + 20
    const mtu = r.pick([576, 1000, 1500, 1420])
    const per = Math.floor((mtu - 20) / 8) * 8
    const data = total - 20
    const count = Math.ceil(data / per)
    if (count < 2) throw new Retry()
    const lastOffset = ((count - 1) * per) / 8
    const lastData = data - (count - 1) * per
    const kind = index % 3
    const base = cs408(`g-cs408-cn-fragment-${nn(index)}`, ['cs408.cn.network.ipv4'], 3)
    const steps = `数据部分 ${total} − 20 = ${data} B；每片数据至多 ${mtu} − 20 = ${mtu - 20} B，且必须是 8 的整数倍，取 ${per} B。\n\n共需 ⌈${data} / ${per}⌉ = ${count} 片；最后一片数据 ${lastData} B，片偏移 = ${count - 1} × ${per} / 8 = ${lastOffset}（以 8 B 为单位），MF = 0。`
    if (kind === 0) {
      const { options, answer } = shuffleOptions(r, `${count}`, [`${Math.ceil(total / mtu)}` === `${count}` ? `${count + 1}` : `${Math.ceil(total / mtu)}`, `${count - 1}`, `${Math.ceil(data / (mtu - 20))}` === `${count}` ? `${count + 2}` : `${Math.ceil(data / (mtu - 20))}`, `${count + 1}`])
      return { ...base, stem: `一个总长度为 ${total} B（首部 20 B，无选项）的 IP 数据报，经过 MTU 为 ${mtu} B 的链路时需要分片，则应分为（ ）个片。`, type: 'single_choice', options, answer, analysis: steps }
    }
    if (kind === 1) {
      const { options, answer } = shuffleOptions(r, `${lastOffset}`, [`${(count - 1) * per}`, `${((count - 1) * (mtu - 20)) / 8}`, `${lastOffset + per / 8}`, `${(count - 1) * mtu / 8}`].map((v) => String(Number(v))))
      return { ...base, stem: `一个总长度为 ${total} B（首部 20 B，无选项）的 IP 数据报经 MTU 为 ${mtu} B 的链路转发，被分成若干片，则最后一个分片首部中的片偏移字段的值为（ ）。`, type: 'single_choice', options, answer, analysis: `${steps}\n\n易错点：片偏移以 8 字节为单位，不是字节数。` }
    }
    const { options, answer } = shuffleOptions(r, `${lastData + 20}`, [`${lastData}`, `${per + 20}`, `${lastData + 40}`, `${mtu}`])
    return { ...base, stem: `一个总长度为 ${total} B（首部 20 B，无选项）的 IP 数据报经 MTU 为 ${mtu} B 的链路转发，被分成若干片，则最后一个分片的总长度为（ ）B。`, type: 'single_choice', options, answer, analysis: `${steps}\n\n最后一片总长度 = 数据 ${lastData} B + 首部 20 B = ${lastData + 20} B。` }
  })
}

function delays() {
  const random = rng(seedOf('cs408-cn-delay'))
  return family(random, 5, (r, index) => {
    const routers = r.int(1, 3)
    const kb = r.pick([1, 2, 4, 8]) // packet size in kbit … use bits
    const bits = kb * 1000 * 8
    const rateMb = r.pick([1, 2, 4, 8, 10])
    const txMs = bits / (rateMb * 1000)
    const propMs = r.pick([1, 2, 5])
    const links = routers + 1
    const correct = links * txMs + links * propMs
    const { options, answer } = shuffleOptions(r, `${fmt(correct, 2)} ms`, [`${fmt(txMs + links * propMs, 2)} ms`, `${fmt(links * txMs + propMs, 2)} ms`, `${fmt(routers * txMs + links * propMs, 2)} ms`, `${fmt(links * (txMs + 2 * propMs), 2)} ms`])
    return {
      ...cs408(`g-cs408-cn-delay-${nn(index)}`, ['cs408.cn.overview.concepts', 'cs408.cn.physical.switching'], 2),
      stem: `主机 A 经过 ${routers} 个路由器（存储转发）向主机 B 发送一个 ${kb * 1000} B 的分组。各段链路的数据传输速率均为 ${rateMb} Mb/s，每段链路的传播时延均为 ${propMs} ms，忽略排队与处理时延。从 A 开始发送到 B 完全收到该分组共需（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `共有 ${links} 段链路。存储转发时，每个结点都要收完整个分组才转发，所以每段链路都有一次发送时延：${kb * 1000} × 8 bit ÷ ${rateMb} Mb/s = ${fmt(txMs, 3)} ms；每段还有传播时延 ${propMs} ms。\n\n总时延 = ${links} × ${fmt(txMs, 3)} + ${links} × ${propMs} = ${fmt(correct, 2)} ms。\n\n易错点：发送时延只算一次是“电路交换/直通”的情形；存储转发要按链路数计算。`,
    }
  })
}

export default {
  id: 'gen-cs408-cn',
  file: 'cs408/gen-cs408-cn.json',
  pack: {
    pack: 'gen-cs408-cn',
    title: '408 计算机网络 · 计算专项（生成）',
    version: 1,
    description: '奈氏准则与香农定理、子网与广播地址、路由聚合、最长前缀匹配、CRC、滑动窗口与信道利用率、CSMA/CD 最短帧长、TCP 拥塞控制与序号确认号、IP 分片、存储转发时延——答案由计算得出。',
    generator: 'content/generators/cs408-cn.mjs',
  },
  build() {
    return [
      ...capacity(),
      ...subnet(),
      ...aggregate(),
      ...longestPrefix(),
      ...crc(),
      ...windows(),
      ...csmacd(),
      ...congestion(),
      ...tcpNumbers(),
      ...fragmentation(),
      ...delays(),
    ]
  },
}
