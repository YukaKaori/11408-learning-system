/**
 * 408 计算机组成原理 — computational families: number representation and
 * arithmetic, IEEE 754, the memory hierarchy, performance, the pipeline,
 * instructions, I/O. Answers are computed bit-exactly (IEEE 754 through a
 * Float32 view); distractors are the classic slips (bias 128 for 127, the
 * hidden bit written out, lines confused with sets, 原码 for 补码).
 */
import { rng, seedOf } from '../lib/random.mjs'
import { shuffleOptions } from '../lib/choice.mjs'
import { cs408, family, fmt, nn, Retry, table } from '../lib/common.mjs'

const hex = (value, digits) => `${value.toString(16).toUpperCase().padStart(digits, '0')}H`
const bin = (value, bits) => value.toString(2).padStart(bits, '0')

// --- 数的表示与运算 ---------------------------------------------------------------------

function fixedPoint() {
  const random = rng(seedOf('cs408-co-fixed'))
  return family(random, 9, (r, index) => {
    const kind = index % 3
    if (kind === 0) {
      const bits = r.pick([8, 8, 16])
      const max = 2 ** (bits - 1)
      const magnitude = r.int(2, max - 1)
      const x = -magnitude
      const digits = bits / 4
      const complement = 2 ** bits + x
      const sign = 2 ** (bits - 1)
      const original = sign + magnitude
      const ones = 2 ** bits - 1 - magnitude
      const { options, answer } = shuffleOptions(r, hex(complement, digits), [hex(original, digits), hex(ones, digits), hex(magnitude, digits), hex(complement + sign > 2 ** bits - 1 ? complement - sign : complement + sign, digits)])
      return {
        ...cs408(`g-cs408-co-fixed-${nn(index)}`, ['cs408.co.data.fixed'], 1),
        stem: `${bits} 位整数 x = ${x}，其补码表示（十六进制）为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `|x| = ${magnitude} = ${bin(magnitude, bits)}B。\n\n原码：符号位置 1 → ${bin(original, bits)}B = ${hex(original, digits)}；\n反码：符号位不变、数值位取反 → ${bin(ones, bits)}B = ${hex(ones, digits)}；\n补码：反码 + 1 → ${bin(complement, bits)}B = ${hex(complement, digits)}。\n\n快速算法：负数的 ${bits} 位补码 = 2^${bits} − |x| = ${2 ** bits} − ${magnitude} = ${complement}。`,
      }
    }
    if (kind === 1) {
      const value = r.int(129, 254)
      const signed = value - 256
      const original = -(value - 128)
      const ones = -(255 - value)
      const { options, answer } = shuffleOptions(r, `${signed}`, [`${value}`, `${original}`, `${ones}`, `${signed + 1}`])
      return {
        ...cs408(`g-cs408-co-fixed-${nn(index)}`, ['cs408.co.data.fixed'], 2),
        stem: `某 8 位寄存器的内容为 ${hex(value, 2)}。若它表示一个用补码表示的带符号整数，则其真值为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `${hex(value, 2)} = ${bin(value, 8)}B，最高位为 1，是负数。\n\n补码真值 = 无符号值 − 2^8 = ${value} − 256 = ${signed}。\n\n对照：按原码解释为 ${original}，按反码解释为 ${ones}，按无符号数解释为 ${value}——题目问的是补码。`,
      }
    }
    const x = r.int(-120, 120)
    if (x === 0) throw new Retry()
    const excess = x + 128
    const complement = (x + 256) % 256
    const { options, answer } = shuffleOptions(r, hex(excess, 2), [hex(complement, 2), hex((x + 127 + 256) % 256, 2), hex((excess + 128) % 256, 2), hex(Math.abs(x), 2)])
    return {
      ...cs408(`g-cs408-co-fixed-${nn(index)}`, ['cs408.co.data.fixed'], 2),
      stem: `8 位移码（偏置值为 128）表示整数 x = ${x}，其机器数（十六进制）为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `移码 = 真值 + 偏置值 = ${x} + 128 = ${excess} = ${bin(excess, 8)}B = ${hex(excess, 2)}。\n\n与补码的关系：同一个数的移码与补码只有符号位相反（补码为 ${hex(complement, 2)}）。\n\n易错点：IEEE 754 阶码的偏置是 127，而这里是 8 位整数移码，偏置为 128。`,
    }
  })
}

function overflow() {
  const random = rng(seedOf('cs408-co-overflow'))
  return family(random, 6, (r, index) => {
    const a = r.int(0, 255)
    const b = r.int(0, 255)
    const subtract = index % 3 === 0
    const sa = a >= 128 ? a - 256 : a
    const sb = b >= 128 ? b - 256 : b
    const exact = subtract ? sa - sb : sa + sb
    const result = ((subtract ? a - b : a + b) % 256 + 256) % 256
    const overflowed = exact < -128 || exact > 127
    if (index % 2 === 0 && !overflowed) throw new Retry()
    const say = (value, of) => `${hex(value, 2)}，${of ? '溢出' : '不溢出'}`
    const { options, answer } = shuffleOptions(r, say(result, overflowed), [say(result, !overflowed), say((result + 1) % 256, overflowed), say(((subtract ? a + b : a - b) % 256 + 256) % 256, overflowed), say((~result) & 255, !overflowed)])
    return {
      ...cs408(`g-cs408-co-overflow-${nn(index)}`, ['cs408.co.data.integer', 'cs408.co.data.fixed'], 2),
      stem: `x、y 为 8 位补码表示的整数，x = ${hex(a, 2)}，y = ${hex(b, 2)}。计算 x ${subtract ? '−' : '+'} y，其结果（8 位）和是否溢出分别为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `x = ${hex(a, 2)} 的真值为 ${sa}，y = ${hex(b, 2)} 的真值为 ${sb}。\n\n${subtract ? `x − y 在机器中按 x + [−y]补 计算` : 'x + y 直接相加'}，保留低 8 位得 ${hex(result, 2)}（真值 ${result >= 128 ? result - 256 : result}）。\n\n真值运算结果为 ${exact}，${overflowed ? '超出' : '落在'} 8 位补码的表示范围 [−128, 127]${overflowed ? '，发生溢出' : '内，不溢出'}。\n\n判别方法：${subtract ? '减法时若两数异号且结果与被减数异号则溢出' : '两个同号数相加，结果符号与它们相反即溢出；异号相加不会溢出'}。`,
    }
  })
}

function float32Hex(value) {
  const view = new DataView(new ArrayBuffer(4))
  view.setFloat32(0, value)
  return view.getUint32(0)
}

function ieee() {
  const random = rng(seedOf('cs408-co-ieee'))
  const FRACTIONS = [0, 0.5, 0.25, 0.75, 0.125, 0.375, 0.625, 0.875]
  return family(random, 9, (r, index) => {
    const magnitude = r.int(1, 99) + r.pick(FRACTIONS)
    const negative = r.chance(0.6)
    const value = negative ? -magnitude : magnitude
    const bits = float32Hex(value)
    const sign = bits >>> 31
    const exponent = (bits >>> 23) & 0xff
    const mantissa = bits & 0x7fffff
    const e = exponent - 127
    const showBits = `${sign} ${bin(exponent, 8)} ${bin(mantissa, 23)}`
    const asHex = (v) => `${(v >>> 0).toString(16).toUpperCase().padStart(8, '0')}H`
    const binary = magnitude.toString(2)
    if (index % 3 === 2) {
      // Decode.
      const correct = fmt(value, 4)
      const { options, answer } = shuffleOptions(r, correct, [fmt(-value, 4), fmt(value * 2, 4), fmt(value / 2, 4), fmt(negative ? -(magnitude + 1) : magnitude + 1, 4)])
      return {
        ...cs408(`g-cs408-co-ieee-${nn(index)}`, ['cs408.co.data.float'], 3),
        stem: `某 float 型变量（IEEE 754 单精度）的机器数为 ${asHex(bits)}，其真值为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `${asHex(bits)} = ${showBits}（符号 | 阶码 | 尾数）。\n\n符号位 ${sign} → ${sign ? '负数' : '正数'}；阶码 ${bin(exponent, 8)}B = ${exponent}，真实指数 = ${exponent} − 127 = ${e}；尾数前补隐藏位 1。\n\n真值 = ${sign ? '−' : ''}1.${bin(mantissa, 23).replace(/0+$/, '') || '0'}B × 2^${e} = ${sign ? '−' : ''}${binary}B = ${correct}。\n\n易错点：偏置值是 127；尾数字段不含最高位的 1。`,
      }
    }
    const wrongBias = (bits & 0x807fffff) | (((exponent + 1) & 0xff) << 23)
    const flipped = bits ^ 0x80000000
    const hiddenWritten = (bits & 0xff800000) | ((mantissa >>> 1) | 0x400000)
    const shifted = (bits & 0x807fffff) | (((exponent - 1) & 0xff) << 23)
    const { options, answer } = shuffleOptions(r, asHex(bits), [asHex(wrongBias), asHex(flipped), asHex(hiddenWritten), asHex(shifted)])
    return {
      ...cs408(`g-cs408-co-ieee-${nn(index)}`, ['cs408.co.data.float'], 3),
      stem: `float 型变量 x = ${fmt(value, 4)}，采用 IEEE 754 单精度格式存储，其机器数（十六进制）为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `|x| = ${fmt(magnitude, 4)} = ${binary}B = 1.${binary.replace('.', '').slice(1).replace(/0+$/, '') || '0'}B × 2^${e}。\n\n符号位 ${sign}；阶码 = ${e} + 127 = ${exponent} = ${bin(exponent, 8)}B；尾数取小数点后的部分（不含隐藏位 1）补足 23 位。\n\n机器数 = ${showBits} = ${asHex(bits)}。\n\n易错点：阶码偏置为 127（误用 128 会得 ${asHex(wrongBias)}）；隐藏位 1 不存储。`,
    }
  })
}

function radix() {
  const random = rng(seedOf('cs408-co-radix'))
  return family(random, 4, (r, index) => {
    const integer = r.int(17, 250)
    const fraction = r.pick([0.5, 0.25, 0.75, 0.125, 0.625, 0.875])
    const value = integer + fraction
    const hexText = `${integer.toString(16).toUpperCase()}.${(fraction * 16).toString(16).toUpperCase()}H`
    const fracBin = fraction.toString(2).slice(2)
    if ((fraction * 16) % 1 !== 0) throw new Retry()
    const { options, answer } = shuffleOptions(r, fmt(value, 4), [fmt(integer + (fraction * 16) / 10, 4), fmt(integer + fraction / 2, 4), fmt(Number.parseInt(integer.toString(16), 10) + fraction, 4), fmt(integer + 1 - fraction, 4)])
    return {
      ...cs408(`g-cs408-co-radix-${nn(index)}`, ['cs408.co.data.radix'], 1),
      stem: `十六进制数 ${hexText} 对应的十进制数为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `整数部分 ${integer.toString(16).toUpperCase()}H = ${integer}；小数部分 0.${(fraction * 16).toString(16).toUpperCase()}H = ${fraction * 16} / 16 = ${fraction}（二进制 0.${fracBin}B）。\n\n合计 ${fmt(value, 4)}。易错点：十六进制小数位的权是 16⁻¹，不是 10⁻¹。`,
    }
  })
}

// --- 存储器层次结构 -----------------------------------------------------------------

function cacheMapping() {
  const random = rng(seedOf('cs408-co-cache-map'))
  return family(random, 9, (r, index) => {
    const cacheKB = r.pick([4, 8, 16, 32, 64])
    const block = r.pick([16, 32, 64])
    const ways = r.pick([1, 2, 4, 8])
    const offset = Math.log2(block)
    const sets = (cacheKB * 1024) / (block * ways)
    const indexBits = Math.log2(sets)
    const tag = 32 - indexBits - offset
    const lines = (cacheKB * 1024) / block
    const mapping = ways === 1 ? '直接映射' : ` ${ways} 路组相联映射`
    const kind = index % 3
    const base = cs408(`g-cs408-co-cache-map-${nn(index)}`, ['cs408.co.memory.cache'], 3)
    const steps = `块大小 ${block} B → 块内地址 log₂${block} = ${offset} 位；Cache 共 ${cacheKB} KB / ${block} B = ${lines} 行，${ways === 1 ? '直接映射时每行即一组' : `每组 ${ways} 行，共 ${lines} / ${ways} = ${sets} 组`} → ${ways === 1 ? '行号' : '组号'} log₂${sets} = ${indexBits} 位；标记位 = 32 − ${indexBits} − ${offset} = ${tag} 位。`
    if (kind === 0) {
      const { options, answer } = shuffleOptions(r, `${tag}`, [`${32 - Math.log2(lines) - offset}`, `${32 - offset}`, `${tag + 1}`, `${tag - 1}`, `${32 - indexBits}`])
      return { ...base, stem: `某计算机主存按字节编址，地址为 32 位，Cache 数据区容量为 ${cacheKB} KB，块大小为 ${block} B，采用${mapping}。主存地址中标记（Tag）字段的位数为（ ）。`, type: 'single_choice', options, answer, analysis: `${steps}\n\n易错点：组相联的“索引”是组号而不是行号——组数 = 行数 / 路数。` }
    }
    if (kind === 1) {
      const { options, answer } = shuffleOptions(r, `${indexBits}`, [`${Math.log2(lines)}`, `${indexBits + 1}`, `${Math.max(1, indexBits - 1)}`, `${offset}`, `${tag}`])
      return { ...base, stem: `某计算机主存按字节编址，地址为 32 位，Cache 数据区容量为 ${cacheKB} KB，块大小为 ${block} B，采用${mapping}。主存地址中${ways === 1 ? '行号（Cache 行索引）' : '组号（组索引）'}字段的位数为（ ）。`, type: 'single_choice', options, answer, analysis: steps }
    }
    const address = r.int(0, 2 ** 24 - 1)
    const set = Math.floor(address / block) % sets
    const { options, answer } = shuffleOptions(r, `${set}`, [`${Math.floor(address / block) % lines}`, `${(set + 1) % sets}`, `${Math.floor(address / (block * ways)) % sets}`, `${address % sets}`])
    return { ...base, stem: `某计算机主存按字节编址，Cache 数据区容量为 ${cacheKB} KB，块大小为 ${block} B，采用${mapping}（组号从 0 开始）。主存地址 ${hex(address, 6)}（即十进制 ${address}）所在的主存块应映射到的 Cache ${ways === 1 ? '行' : '组'}号为（ ）。`, type: 'single_choice', options, answer, analysis: `${steps}\n\n主存块号 = ${address} / ${block} 取整 = ${Math.floor(address / block)}；${ways === 1 ? '行' : '组'}号 = 块号 mod ${sets} = ${set}。\n\n易错点：先求块号再取模；组相联的模数是组数 ${sets}，不是行数 ${lines}。` }
  })
}

function hitRate() {
  const random = rng(seedOf('cs408-co-hit-rate'))
  return family(random, 5, (r, index) => {
    const block = r.pick([16, 32, 64, 128])
    const n = r.pick([1000, 2048, 4096, 10000])
    const write = index % 2 === 0
    const perBlock = block / 4
    const accesses = write ? 2 : 1
    const rate = 1 - 1 / (perBlock * accesses)
    const pct = (x) => `${fmt(x * 100, 2)}%`
    const { options, answer } = shuffleOptions(r, pct(rate), [pct(1 - 1 / (perBlock * 2 * accesses)), pct(1 / (perBlock * accesses)), pct(1 - 1 / block), pct(1 - 2 / (perBlock * accesses)), pct(1 - 1 / perBlock)])
    return {
      ...cs408(`g-cs408-co-hit-rate-${nn(index)}`, ['cs408.co.memory.cache', 'cs408.co.isa.machine-code'], 3),
      stem: `某计算机按字节编址，int 型占 4 B，Cache 块大小为 ${block} B，初始为空，容量足以容纳整个数组。数组 int a[${n}] 的首地址与 Cache 块对齐，执行\n\n\`\`\`c\nfor (i = 0; i < ${n}; i++)\n    ${write ? 'a[i] = a[i] + 1;' : 'sum += a[i];'}\n\`\`\`\n\n${write ? '（每次循环先读 a[i] 再写 a[i]，写操作命中时直接写入 Cache；i 在寄存器中）' : '（sum、i 在寄存器中）'}对数组 a 的访问命中率约为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `每块可放 ${block} / 4 = ${perBlock} 个元素。顺序访问时，每块只有第一次读 a[i] 缺失，之后${write ? '同一元素的写以及' : ''}本块其余元素的访问都命中。\n\n每块共 ${perBlock} × ${accesses} = ${perBlock * accesses} 次访问、1 次缺失，命中率 = 1 − 1/${perBlock * accesses} = ${pct(rate)}。\n\n易错点：${write ? '每个元素被访问两次（读和写），写的那次一定命中' : '缺失只发生在每块的第一个元素'}；命中率与数组长度无关。`,
    }
  })
}

function memoryExpansion() {
  const random = rng(seedOf('cs408-co-memory'))
  return family(random, 6, (r, index) => {
    const chipK = r.pick([1, 2, 4, 8, 16])
    const chipBits = r.pick([1, 4, 8])
    const memK = chipK * r.pick([2, 4, 8, 16])
    const memBits = r.pick([8, 16])
    if (chipBits > memBits) throw new Retry()
    const chips = (memK / chipK) * (memBits / chipBits)
    const kind = index % 2
    if (kind === 0) {
      const { options, answer } = shuffleOptions(r, `${chips}`, [`${memK / chipK}`, `${memBits / chipBits}`, `${chips * 2}`, `${chips / 2}`])
      return {
        ...cs408(`g-cs408-co-memory-${nn(index)}`, ['cs408.co.memory.main'], 2),
        stem: `用 ${chipK}K × ${chipBits} 位的存储芯片构成 ${memK}K × ${memBits} 位的主存储器，共需要（ ）片芯片。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `位扩展：每组需 ${memBits} / ${chipBits} = ${memBits / chipBits} 片拼成 ${memBits} 位；字扩展：需 ${memK}K / ${chipK}K = ${memK / chipK} 组。\n\n共需 ${memBits / chipBits} × ${memK / chipK} = ${chips} 片。也可直接用容量相除：(${memK}K × ${memBits}) / (${chipK}K × ${chipBits}) = ${chips}。`,
      }
    }
    const select = Math.log2(memK / chipK)
    const chipAddr = Math.log2(chipK * 1024)
    const { options, answer } = shuffleOptions(r, `${chipAddr}，${select}`, [`${chipAddr + select}，${select}`, `${chipAddr}，${memK / chipK}`, `${select}，${chipAddr}`, `${chipAddr - 1}，${select + 1}`])
    return {
      ...cs408(`g-cs408-co-memory-${nn(index)}`, ['cs408.co.memory.main'], 3),
      stem: `用 ${chipK}K × ${chipBits} 位的芯片构成 ${memK}K × ${memBits} 位、按字编址的存储器（字长 ${memBits} 位）。片内地址线和用于片选译码的地址线分别为（ ）根。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `存储器共 ${memK}K 个字，需地址线 log₂(${memK}K) = ${chipAddr + select} 根。\n\n每片 ${chipK}K 个单元，片内地址 log₂(${chipK}K) = ${chipAddr} 根；共 ${memK / chipK} 组芯片，片选需 log₂${memK / chipK} = ${select} 根（经译码产生 ${memK / chipK} 个片选信号）。`,
    }
  })
}

function disk() {
  const random = rng(seedOf('cs408-co-disk'))
  return family(random, 5, (r, index) => {
    const rpm = r.pick([6000, 7500, 10000, 12000, 15000])
    const seek = r.pick([4, 5, 6, 8, 10])
    const sectors = r.pick([100, 200, 250, 400, 500])
    const revMs = 60000 / rpm
    const rotate = revMs / 2
    const transfer = revMs / sectors
    const correct = seek + rotate + transfer
    const ms = (x) => `${fmt(x, 3)} ms`
    const { options, answer } = shuffleOptions(r, ms(correct), [ms(seek + revMs + transfer), ms(seek + rotate), ms(rotate + transfer), ms(seek + revMs)])
    return {
      ...cs408(`g-cs408-co-disk-${nn(index)}`, ['cs408.co.memory.external', 'cs408.os.io.disk'], 2),
      stem: `某磁盘转速为 ${rpm} 转/分，平均寻道时间为 ${seek} ms，每个磁道有 ${sectors} 个扇区。读取一个扇区的平均访问时间为（ ）。（忽略控制器延迟）`,
      type: 'single_choice',
      options,
      answer,
      analysis: `转一圈用时 60 / ${rpm} s = ${fmt(revMs, 3)} ms。\n\n平均访问时间 = 平均寻道时间 + 平均旋转延迟（半圈）+ 传输一个扇区的时间\n= ${seek} + ${fmt(rotate, 3)} + ${fmt(revMs, 3)} / ${sectors}\n= ${ms(correct)}。\n\n易错点：平均旋转延迟是转半圈的时间，不是一整圈。`,
    }
  })
}

// --- 性能、流水线、指令 -------------------------------------------------------------------

function performance() {
  const random = rng(seedOf('cs408-co-performance'))
  return family(random, 6, (r, index) => {
    const f = r.pick([400, 500, 800, 1000, 2000])
    const classes = [
      { name: '算术逻辑', share: 0, cpi: r.int(1, 2) },
      { name: '访存', share: 0, cpi: r.int(2, 4) },
      { name: '转移', share: 0, cpi: r.int(2, 5) },
    ]
    const split = r.pick([[50, 30, 20], [60, 30, 10], [40, 40, 20], [50, 40, 10], [60, 20, 20]])
    classes.forEach((c, i) => (c.share = split[i]))
    const cpi = classes.reduce((sum, c) => sum + (c.share / 100) * c.cpi, 0)
    const mips = f / cpi
    const kind = index % 2
    if (kind === 0) {
      const { options, answer } = shuffleOptions(r, fmt(cpi, 2), [fmt(classes.reduce((s, c) => s + c.cpi, 0) / 3, 2), fmt(cpi + 0.5, 2), fmt(Math.max(...classes.map((c) => c.cpi)), 2), fmt(cpi - 0.3, 2)])
      return {
        ...cs408(`g-cs408-co-performance-${nn(index)}`, ['cs408.co.overview.performance'], 2),
        stem: `某程序在主频 ${f} MHz 的 CPU 上运行，各类指令所占比例和 CPI 如下表。该程序的平均 CPI 为（ ）。\n\n${table(['指令类型', '所占比例', 'CPI'], classes.map((c) => [c.name, `${c.share}%`, c.cpi]))}`,
        type: 'single_choice',
        options,
        answer,
        analysis: `平均 CPI = Σ 比例 × CPI = ${classes.map((c) => `${c.share}% × ${c.cpi}`).join(' + ')} = ${fmt(cpi, 2)}。\n\n易错点：要按比例加权，不能直接对各类 CPI 求平均。`,
      }
    }
    const { options, answer } = shuffleOptions(r, fmt(mips, 2), [fmt(f * cpi, 2), fmt(f / (cpi + 1), 2), fmt(f, 2), fmt(f / Math.max(...classes.map((c) => c.cpi)), 2)])
    return {
      ...cs408(`g-cs408-co-performance-${nn(index)}`, ['cs408.co.overview.performance'], 2),
      stem: `某程序在主频 ${f} MHz 的 CPU 上运行，各类指令所占比例和 CPI 如下表。该机器运行此程序的 MIPS 数为（ ）。\n\n${table(['指令类型', '所占比例', 'CPI'], classes.map((c) => [c.name, `${c.share}%`, c.cpi]))}`,
      type: 'single_choice',
      options,
      answer,
      analysis: `平均 CPI = ${classes.map((c) => `${c.share}% × ${c.cpi}`).join(' + ')} = ${fmt(cpi, 2)}。\n\nMIPS = 主频 / (CPI × 10⁶) = ${f} × 10⁶ / (${fmt(cpi, 2)} × 10⁶) = ${fmt(mips, 2)}。`,
    }
  })
}

function pipeline() {
  const random = rng(seedOf('cs408-co-pipeline'))
  return family(random, 6, (r, index) => {
    const k = r.pick([4, 5, 5, 6])
    const t = r.pick([1, 2, 5, 10])
    const n = r.pick([10, 20, 50, 100])
    const total = (k + n - 1) * t
    const kind = index % 3
    if (kind === 0) {
      const { options, answer } = shuffleOptions(r, `${total} ns`, [`${n * k * t} ns`, `${(k + n) * t} ns`, `${n * t} ns`, `${(k * n - 1) * t} ns`])
      return {
        ...cs408(`g-cs408-co-pipeline-${nn(index)}`, ['cs408.co.cpu.pipeline'], 2),
        stem: `一条 ${k} 段的指令流水线，各段执行时间均为 ${t} ns。在理想情况下（无任何冲突）执行 ${n} 条指令，所需时间为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `第一条指令经过 ${k} 段需 ${k} × ${t} ns，之后每 ${t} ns 完成一条：总时间 = (k + n − 1) × Δt = (${k} + ${n} − 1) × ${t} = ${total} ns。\n\n不用流水线需 ${n} × ${k} × ${t} = ${n * k * t} ns。`,
      }
    }
    if (kind === 1) {
      const speedup = (n * k) / (k + n - 1)
      const { options, answer } = shuffleOptions(r, fmt(speedup, 2), [`${k}`, fmt(n / (k + n - 1), 2), fmt((n * k) / (k + n), 2), fmt(speedup + 1, 2)])
      return {
        ...cs408(`g-cs408-co-pipeline-${nn(index)}`, ['cs408.co.cpu.pipeline'], 3),
        stem: `一条 ${k} 段的指令流水线，各段执行时间均为 ${t} ns。在理想情况下连续执行 ${n} 条指令，与不采用流水线（每条指令依次执行全部 ${k} 段）相比，加速比为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `加速比 = 不用流水线的时间 / 流水线时间 = (${n} × ${k} × ${t}) / ((${k} + ${n} − 1) × ${t}) = ${n * k} / ${k + n - 1} ≈ ${fmt(speedup, 2)}。\n\n当 n → ∞ 时加速比趋近于段数 ${k}，但有限条指令时达不到 ${k}。`,
      }
    }
    const throughput = n / total // instructions per ns
    const mips = throughput * 1000
    const { options, answer } = shuffleOptions(r, fmt(mips, 2), [fmt(1000 / t, 2), fmt((n / (n * k * t)) * 1000, 2), fmt((n / ((k + n) * t)) * 1000, 2), fmt(1000 / (k * t), 2)])
    return {
      ...cs408(`g-cs408-co-pipeline-${nn(index)}`, ['cs408.co.cpu.pipeline'], 3),
      stem: `一条 ${k} 段的指令流水线，各段执行时间均为 ${t} ns。在理想情况下连续执行 ${n} 条指令，其实际吞吐率为（ ）MIPS（百万条指令/秒）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `执行 ${n} 条指令共需 (${k} + ${n} − 1) × ${t} = ${total} ns。\n\n吞吐率 = ${n} / ${total} ns = ${fmt(throughput, 4)} 条/ns = ${fmt(mips, 2)} MIPS。\n\n易错点：最大吞吐率 1/Δt = ${fmt(1000 / t, 2)} MIPS 只有在指令数趋于无穷时才能达到。`,
    }
  })
}

function addressing() {
  const random = rng(seedOf('cs408-co-addressing'))
  const MODES = ['direct', 'indirect', 'register-indirect', 'base', 'relative', 'immediate']
  return family(random, 6, (r, index) => {
    const mode = MODES[(index - 1) % MODES.length]
    const A = r.int(0x100, 0x1ff)
    const R = r.int(0x200, 0x2ff)
    const B = r.int(0x300, 0x3ff)
    const PC = r.int(0x1000, 0x10ff)
    const len = 2
    const memory = {
      [A]: r.int(0x400, 0x4ff),
      [R]: r.int(0x500, 0x5ff),
      [(B + A) & 0xffff]: r.int(0x600, 0x6ff),
      [(PC + len + A) & 0xffff]: r.int(0x700, 0x7ff),
    }
    const h = (v) => `${v.toString(16).toUpperCase()}H`
    const ea = {
      direct: A,
      indirect: memory[A],
      'register-indirect': R,
      base: B + A,
      relative: PC + len + A,
    }
    const operand = mode === 'immediate' ? A : memory[ea[mode]] ?? null
    const askOperand = mode === 'immediate' || mode === 'indirect'
    const NAMES = { direct: '直接寻址', indirect: '间接寻址', 'register-indirect': '寄存器间接寻址（寄存器 R）', base: '基址寻址（基址寄存器 BR）', relative: '相对寻址', immediate: '立即寻址' }
    if (memory[ea.indirect] === undefined) {
      memory[ea.indirect] = r.int(0x800, 0x8ff)
    }
    const correct = askOperand ? h(mode === 'immediate' ? A : memory[ea.indirect]) : h(ea[mode])
    const pool = [h(A), h(memory[A]), h(R), h(B + A), h(PC + A), h(PC + len + A), h(memory[R])]
    const { options, answer } = shuffleOptions(r, correct, pool.filter((v) => v !== correct))
    const rows = Object.entries(memory).map(([address, value]) => [h(Number(address)), h(value)])
    return {
      ...cs408(`g-cs408-co-addressing-${nn(index)}`, ['cs408.co.isa.addressing'], 2),
      stem: `某机器按字编址，指令长度为 ${len} 个字。一条指令的形式地址 A = ${h(A)}，采用${NAMES[mode]}。当前 PC = ${h(PC)}（取指后 PC 已自动加 ${len}），R = ${h(R)}，BR = ${h(B)}，部分主存单元内容如下表。该指令${askOperand ? '的操作数' : '操作数的有效地址 EA'}为（ ）。\n\n${table(['地址', '内容'], rows)}`,
      type: 'single_choice',
      options,
      answer,
      analysis: {
        direct: `直接寻址：EA = A = ${h(A)}。`,
        indirect: `间接寻址：EA = (A) = (${h(A)}) = ${h(memory[A])}，操作数 = (EA) = ${h(memory[ea.indirect])}。`,
        'register-indirect': `寄存器间接寻址：EA = (R) = ${h(R)}。`,
        base: `基址寻址：EA = (BR) + A = ${h(B)} + ${h(A)} = ${h(B + A)}。`,
        relative: `相对寻址：EA = (PC) + A，PC 为取指后的值 ${h(PC)} + ${len} = ${h(PC + len)}，EA = ${h(PC + len)} + ${h(A)} = ${h(PC + len + A)}。`,
        immediate: `立即寻址：形式地址 A 就是操作数本身，操作数 = ${h(A)}，无需访存。`,
      }[mode] + `\n\n易错点：区分“有效地址”与“操作数”；相对寻址用的是取指后已更新的 PC。`,
    }
  })
}

function expandingOpcode() {
  const random = rng(seedOf('cs408-co-opcode'))
  return family(random, 4, (r, index) => {
    const three = r.int(10, 15)
    const twoMax = (16 - three) * 16
    const two = r.int(Math.max(1, twoMax - 40), twoMax - 1)
    const one = (twoMax - two) * 16
    const { options, answer } = shuffleOptions(r, `${one}`, [`${twoMax - two}`, `${(16 - three) * 16 * 16 - two}`, `${one - 16}`, `${(twoMax - two) * 16 + 16}`])
    return {
      ...cs408(`g-cs408-co-opcode-${nn(index)}`, ['cs408.co.isa.format'], 3),
      stem: `某机器指令字长 16 位，每个地址码 4 位，采用扩展操作码技术。已设计了 ${three} 条三地址指令和 ${two} 条二地址指令，则最多还可以设计（ ）条一地址指令。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `三地址指令操作码 4 位，共 2⁴ = 16 种编码，用去 ${three} 种，剩 ${16 - three} 种作为扩展标志。\n\n二地址指令操作码 8 位：最多 ${16 - three} × 2⁴ = ${twoMax} 条，用去 ${two} 条，剩 ${twoMax - two} 种扩展。\n\n一地址指令操作码 12 位：最多 ${twoMax - two} × 2⁴ = ${one} 条。`,
    }
  })
}

function alignment() {
  const random = rng(seedOf('cs408-co-alignment'))
  return family(random, 6, (r, index) => {
    if (index % 2 === 1) {
      const value = r.int(0x10000000, 0x7fffffff)
      const bytes = [24, 16, 8, 0].map((s) => (value >>> s) & 0xff)
      const k = r.int(0, 3)
      const little = r.chance(0.5)
      const correct = little ? bytes[3 - k] : bytes[k]
      const hx = (v) => `${v.toString(16).toUpperCase().padStart(2, '0')}H`
      const { options, answer } = shuffleOptions(r, hx(correct), bytes.map(hx).filter((v) => v !== hx(correct)))
      return {
        ...cs408(`g-cs408-co-alignment-${nn(index)}`, ['cs408.co.isa.alignment'], 2),
        stem: `某计算机按字节编址，采用${little ? '小端（Little Endian）' : '大端（Big Endian）'}方式存放数据。int 型变量 x = ${value.toString(16).toUpperCase()}H 的首地址为 2000H，则地址 ${(0x2000 + k).toString(16).toUpperCase()}H 中存放的字节为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `${little ? '小端：最低有效字节存放在最低地址' : '大端：最高有效字节存放在最低地址'}。\n\n2000H~2003H 依次存放 ${(little ? [...bytes].reverse() : bytes).map(hx).join('、')}，所以 ${(0x2000 + k).toString(16).toUpperCase()}H 中为 ${hx(correct)}。`,
      }
    }
    const fields = r.shuffle([
      { type: 'char', size: 1 },
      { type: 'int', size: 4 },
      { type: 'short', size: 2 },
      { type: 'char', size: 1 },
    ]).slice(0, r.int(3, 4))
    let offset = 0
    const layout = []
    for (const field of fields) {
      offset = Math.ceil(offset / field.size) * field.size
      layout.push(`${field.type} 占 ${offset}~${offset + field.size - 1}`)
      offset += field.size
    }
    const align = Math.max(...fields.map((f) => f.size))
    const size = Math.ceil(offset / align) * align
    const packed = fields.reduce((s, f) => s + f.size, 0)
    if (size === packed) throw new Retry()
    const { options, answer } = shuffleOptions(r, `${size}`, [`${packed}`, `${offset}`, `${size + 4}`, `${fields.length * 4}`])
    const names = 'abcd'
    return {
      ...cs408(`g-cs408-co-alignment-${nn(index)}`, ['cs408.co.isa.alignment', 'cs408.co.isa.machine-code'], 3),
      stem: `在按字节编址、要求边界对齐（各类型按其自身大小对齐，结构体总大小为最大成员大小的整数倍）的 32 位机器上，char、short、int 分别占 1、2、4 B。下列结构体变量所占的存储空间为（ ）B。\n\n\`\`\`c\nstruct S {\n${fields.map((f, i) => `    ${f.type} ${names[i]};`).join('\n')}\n};\n\`\`\``,
      type: 'single_choice',
      options,
      answer,
      analysis: `逐个成员按其大小对齐：${layout.join('；')}。末尾偏移为 ${offset}，结构体总大小还要补齐为最大成员（${align} B）的整数倍：${size} B。\n\n易错点：成员前的填充与结构体末尾的填充都要算；不对齐时的 ${packed} B 不是答案。`,
    }
  })
}

// --- 总线与 I/O ----------------------------------------------------------------------

function io() {
  const random = rng(seedOf('cs408-co-io'))
  return family(random, 6, (r, index) => {
    const f = r.pick([100, 200, 500, 1000]) // MHz
    if (index % 2 === 1) {
      const rateKB = r.pick([50, 100, 200, 400])
      const unit = r.pick([2, 4])
      const cycles = r.pick([100, 200, 400, 500])
      const perSecond = (rateKB * 1000) / unit
      const fraction = (perSecond * cycles) / (f * 1e6)
      const pct = (x) => `${fmt(x * 100, 2)}%`
      const { options, answer } = shuffleOptions(r, pct(fraction), [pct(fraction / unit), pct(fraction * 2), pct((rateKB * 1000 * cycles) / (f * 1e6)), pct(fraction / 10)])
      return {
        ...cs408(`g-cs408-co-io-${nn(index)}`, ['cs408.co.io.interrupt'], 3),
        stem: `某外设的数据传输率为 ${rateKB} KB/s（1 KB = 1000 B），采用中断方式与主机交换数据，每次中断传送 ${unit} B，中断服务程序（含中断响应）需执行 ${cycles} 个时钟周期。CPU 主频为 ${f} MHz，则 CPU 用于该外设 I/O 的时间占整个 CPU 时间的百分比为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `每秒中断次数 = ${rateKB} × 1000 / ${unit} = ${perSecond} 次；每秒用于中断的时钟周期 = ${perSecond} × ${cycles} = ${perSecond * cycles}。\n\n占比 = ${perSecond * cycles} / (${f} × 10⁶) = ${pct(fraction)}。`,
      }
    }
    const width = r.pick([16, 32, 64])
    const cyclesPer = r.pick([1, 2, 4])
    const bandwidth = (width / 8) * f / cyclesPer
    const { options, answer } = shuffleOptions(r, `${fmt(bandwidth, 2)} MB/s`, [`${fmt(width * f / cyclesPer, 2)} MB/s`, `${fmt((width / 8) * f, 2)} MB/s`, `${fmt((width / 8) * f * cyclesPer, 2)} MB/s`, `${fmt(bandwidth / 2, 2)} MB/s`])
    return {
      ...cs408(`g-cs408-co-io-${nn(index)}`, ['cs408.co.io.bus'], 2),
      stem: `某同步总线的时钟频率为 ${f} MHz，数据线宽度为 ${width} 位，每${cyclesPer === 1 ? '个时钟周期' : ` ${cyclesPer} 个时钟周期`}传送一次数据。该总线的最大数据传输率（带宽）为（ ）。（1 MB = 10⁶ B）`,
      type: 'single_choice',
      options,
      answer,
      analysis: `每次传送 ${width} / 8 = ${width / 8} B，每秒传送 ${f} × 10⁶ / ${cyclesPer} 次。\n\n带宽 = ${width / 8} B × ${f} × 10⁶ / ${cyclesPer} = ${fmt(bandwidth, 2)} MB/s。\n\n易错点：总线宽度以“位”给出，要除以 8 换算成字节。`,
    }
  })
}

export default {
  id: 'gen-cs408-co',
  file: 'cs408/gen-cs408-co.json',
  pack: {
    pack: 'gen-cs408-co',
    title: '408 计算机组成原理 · 计算专项（生成）',
    version: 1,
    description: '原码补码移码、溢出判断、IEEE 754、进制转换、Cache 地址映射与命中率、存储器扩展、磁盘访问时间、CPI 与 MIPS、流水线、寻址方式、扩展操作码、大小端与对齐、中断开销与总线带宽——答案由计算得出（IEEE 754 按位精确）。',
    generator: 'content/generators/cs408-co.mjs',
  },
  build() {
    return [
      ...fixedPoint(),
      ...overflow(),
      ...ieee(),
      ...radix(),
      ...cacheMapping(),
      ...hitRate(),
      ...memoryExpansion(),
      ...disk(),
      ...performance(),
      ...pipeline(),
      ...addressing(),
      ...expandingOpcode(),
      ...alignment(),
      ...io(),
    ]
  },
}
