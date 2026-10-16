/**
 * 408 数据结构 — computational families. Each answer is the algorithm run
 * (stack simulation, tree reconstruction, AVL rotations, Floyd heap building,
 * the 严蔚敏 partition, KMP next, Dijkstra, Kruskal, the critical path…); each
 * distractor is what a slightly wrong algorithm produces; each 解析 shows the run.
 * Conventions that differ between textbooks are stated in the stem.
 */
import { rng, seedOf } from '../lib/random.mjs'
import { shuffleOptions } from '../lib/choice.mjs'
import { cs408, family, fmt, nn, Retry, table } from '../lib/common.mjs'
import { Q } from '../lib/rational.mjs'

const LABELS = 'ABCDEFGHIJ'
const seq = (list) => list.join(', ')

// --- 栈与队列 -------------------------------------------------------------------------

function canPop(order) {
  const stack = []
  let next = 1
  for (const want of order) {
    while (next <= want) stack.push(next++)
    if (stack[stack.length - 1] !== want) return { ok: false, stack: [...stack], want }
    stack.pop()
  }
  return { ok: true }
}

function randomValid(r, n) {
  const order = []
  const stack = []
  let next = 1
  while (order.length < n) {
    if (next <= n && (stack.length === 0 || r.chance(0.5))) stack.push(next++)
    else order.push(stack.pop())
  }
  return order
}

function stackSequences() {
  const random = rng(seedOf('cs408-ds-stack'))
  return family(random, 8, (r, index) => {
    const n = r.pick([5, 5, 6])
    let bad = null
    for (let i = 0; i < 50 && !bad; i++) {
      const candidate = r.shuffle(Array.from({ length: n }, (_, k) => k + 1))
      if (!canPop(candidate).ok) bad = candidate
    }
    const valid = new Set()
    let guard = 0
    while (valid.size < 3 && guard++ < 200) valid.add(seq(randomValid(r, n)))
    if (!bad || valid.size < 3) throw new Retry()
    const failure = canPop(bad)
    const { options, answer } = shuffleOptions(r, seq(bad), [...valid])
    return {
      ...cs408(`g-cs408-ds-stack-${nn(index)}`, ['cs408.ds.stack.stack'], 2),
      stem: `元素 ${seq(Array.from({ length: n }, (_, k) => k + 1))} 依次进栈，进栈过程中可以随时出栈。下列序列中，不可能是出栈序列的是（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `模拟 ${seq(bad)}：要输出 ${failure.want} 时，栈中自底向上为 [${seq(failure.stack)}]，栈顶是 ${failure.stack[failure.stack.length - 1]}，而 ${failure.want} 被压在下面无法先出栈，故不可能。\n\n判别规律：对出栈序列中的任一元素 x，排在 x 之后、且比 x 先进栈的元素必须按逆序出栈。其余三个序列都能通过模拟得到。`,
    }
  })
}

function circularQueue() {
  const random = rng(seedOf('cs408-ds-queue'))
  return family(random, 6, (r, index) => {
    const m = r.int(10, 60)
    const front = r.int(0, m - 1)
    const rear = r.int(0, m - 1)
    if (front === rear) throw new Retry()
    const rearIsNext = index % 2 === 1
    const count = rearIsNext ? (rear - front + m) % m : (rear - front + 1 + m) % m
    const { options, answer } = shuffleOptions(r, `${count}`, [`${(front - rear + m) % m}`, `${rear - front}`, `${rearIsNext ? (rear - front + 1 + m) % m : (rear - front + m) % m}`, `${(count + 1) % m}`])
    return {
      ...cs408(`g-cs408-ds-queue-${nn(index)}`, ['cs408.ds.stack.queue'], 2),
      stem: `用数组 A[0..${m - 1}] 实现循环队列，front 指向队头元素，rear 指向${rearIsNext ? '队尾元素的下一个位置' : '队尾元素'}。若当前 front = ${front}，rear = ${rear}（队列非空），则队列中的元素个数为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: rearIsNext
        ? `rear 指向队尾的下一个位置时，元素个数 = (rear − front + m) % m = (${rear} − ${front} + ${m}) % ${m} = ${count}。`
        : `rear 指向队尾元素时，队尾比“下一个位置”少 1，元素个数 = (rear − front + 1 + m) % m = (${rear} − ${front} + 1 + ${m}) % ${m} = ${count}。`,
    }
  })
}

// --- 树与二叉树 -----------------------------------------------------------------------

function randomTree(r, n) {
  const labels = LABELS.slice(0, n).split('')
  const build = (items) => {
    if (items.length === 0) return null
    const k = r.int(0, items.length - 1)
    return { label: items[k], left: build(items.slice(0, k)), right: build(items.slice(k + 1)) }
  }
  // An in-order of A..: build from a shuffled in-order so labels are not alphabetical in pre-order.
  return build(r.shuffle(labels))
}

const pre = (t) => (t ? [t.label, ...pre(t.left), ...pre(t.right)] : [])
const ino = (t) => (t ? [...ino(t.left), t.label, ...ino(t.right)] : [])
const post = (t) => (t ? [...post(t.left), ...post(t.right), t.label] : [])
const mirror = (t) => (t ? { label: t.label, left: mirror(t.right), right: mirror(t.left) } : null)
const height = (t) => (t ? 1 + Math.max(height(t.left), height(t.right)) : 0)

function traversal() {
  const random = rng(seedOf('cs408-ds-traversal'))
  return family(random, 8, (r, index) => {
    const n = r.int(7, 9)
    const tree = randomTree(r, n)
    if (height(tree) < 4) throw new Retry()
    const fromPre = index % 2 === 1
    const given = fromPre ? pre(tree) : post(tree)
    const asked = fromPre ? post(tree) : pre(tree)
    const walk = fromPre ? post : pre
    const swappedRoot = { label: tree.label, left: tree.right, right: tree.left }
    const levelOrder = []
    const queue = [tree]
    while (queue.length) {
      const node = queue.shift()
      levelOrder.push(node.label)
      if (node.left) queue.push(node.left)
      if (node.right) queue.push(node.right)
    }
    const nearMiss = [...asked]
    const at = r.int(1, nearMiss.length - 3)
    ;[nearMiss[at], nearMiss[at + 1]] = [nearMiss[at + 1], nearMiss[at]]
    const wrong = [
      walk(swappedRoot), // left and right subtrees exchanged at the root
      walk(mirror(tree)), // = the given sequence reversed: a tempting shortcut
      levelOrder,
      nearMiss,
    ].map((list) => list.join(''))
    const { options, answer } = shuffleOptions(r, asked.join(''), wrong)
    return {
      ...cs408(`g-cs408-ds-traversal-${nn(index)}`, ['cs408.ds.tree.traversal'], 3),
      stem: `已知一棵二叉树的${fromPre ? '先序' : '后序'}遍历序列为 ${given.join('')}，中序遍历序列为 ${ino(tree).join('')}，则其${fromPre ? '后序' : '先序'}遍历序列为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `${fromPre ? '先序的第一个' : '后序的最后一个'}结点 ${tree.label} 是根；在中序 ${ino(tree).join('')} 中，${tree.label} 左边是左子树（${ino(tree.left).join('') || '空'}），右边是右子树（${ino(tree.right).join('') || '空'}）。对左右子树递归同样的划分，即可还原整棵树。\n\n还原后：先序 ${pre(tree).join('')}，中序 ${ino(tree).join('')}，后序 ${post(tree).join('')}。\n\n易错点：中序序列是划分左右子树的依据；只有先序与后序不能唯一确定一棵二叉树。`,
    }
  })
}

function treeProperties() {
  const random = rng(seedOf('cs408-ds-tree-props'))
  return family(random, 8, (r, index) => {
    const kind = index % 4
    if (kind === 0) {
      const n = r.int(100, 999)
      const leaves = Math.ceil(n / 2)
      const { options, answer } = shuffleOptions(r, `${leaves}`, [`${Math.floor(n / 2)}`, `${leaves + 1}`, `${n - leaves - 1}`, `${Math.floor(n / 2) + 1 === leaves ? leaves - 1 : Math.floor(n / 2) + 1}`])
      return {
        ...cs408(`g-cs408-ds-tree-props-${nn(index)}`, ['cs408.ds.tree.binary'], 2),
        stem: `一棵有 ${n} 个结点的完全二叉树，其叶子结点的个数为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `完全二叉树按层序编号 1~${n}，编号 > ⌊${n}/2⌋ = ${Math.floor(n / 2)} 的结点都是叶子，所以叶子数 = ${n} − ${Math.floor(n / 2)} = ${leaves}。\n\n${n % 2 === 0 ? `n 为偶数，有 1 个度为 1 的结点。` : `n 为奇数，没有度为 1 的结点。`}由 n0 = n2 + 1 也可验证。`,
      }
    }
    if (kind === 1) {
      const n = r.int(200, 3000)
      const h = Math.floor(Math.log2(n)) + 1
      const { options, answer } = shuffleOptions(r, `${h}`, [`${h - 1}`, `${h + 1}`, `${Math.ceil(Math.log2(n))}` === `${h}` ? `${h + 2}` : `${Math.ceil(Math.log2(n))}`, `${h - 2}`])
      return {
        ...cs408(`g-cs408-ds-tree-props-${nn(index)}`, ['cs408.ds.tree.binary'], 1),
        stem: `具有 ${n} 个结点的完全二叉树的高度（根为第 1 层）为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `高度 h = ⌊log₂n⌋ + 1 = ⌊log₂${n}⌋ + 1 = ${h - 1} + 1 = ${h}（因为 2^${h - 1} = ${2 ** (h - 1)} ≤ ${n} < 2^${h} = ${2 ** h}）。`,
      }
    }
    if (kind === 2) {
      const n2 = r.int(10, 60)
      const n1 = r.int(5, 40)
      const leaves = n2 + 1
      const { options, answer } = shuffleOptions(r, `${leaves}`, [`${n2}`, `${n2 + n1}`, `${n2 - 1}`, `${n1 + 1}`])
      return {
        ...cs408(`g-cs408-ds-tree-props-${nn(index)}`, ['cs408.ds.tree.binary', 'cs408.ds.tree.concepts'], 2),
        stem: `一棵二叉树中有 ${n2} 个度为 2 的结点、${n1} 个度为 1 的结点，则该二叉树的叶子结点数为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `结点总数 n = n0 + n1 + n2；分支总数 = n − 1 = n1 + 2n2。两式联立得 n0 = n2 + 1 = ${leaves}，与度为 1 的结点数无关。`,
      }
    }
    const degree = r.pick([3, 4])
    // n0 = 1 + Σ (i − 1) · n_i over the degrees i ≥ 1
    const byDegree = Array.from({ length: degree }, (_, i) => ({ degree: i + 1, count: i === 0 ? r.int(2, 10) : r.int(1, 12) }))
    const n0 = 1 + byDegree.reduce((sum, d) => sum + (d.degree - 1) * d.count, 0)
    const { options, answer } = shuffleOptions(r, `${n0}`, [`${n0 - 1}`, `${byDegree.reduce((s, d) => s + d.degree * d.count, 0)}`, `${n0 + byDegree[0].count}`, `${byDegree.reduce((s, d) => s + d.count, 0)}`])
    return {
      ...cs408(`g-cs408-ds-tree-props-${nn(index)}`, ['cs408.ds.tree.concepts'], 3),
      stem: `一棵度为 ${degree} 的树中，${byDegree.map((d) => `度为 ${d.degree} 的结点有 ${d.count} 个`).join('，')}，则该树的叶子结点数为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `设叶子数为 n0。结点总数 n = n0 + ${byDegree.map((d) => d.count).join(' + ')}；除根外每个结点对应一条分支，分支数 = n − 1 = ${byDegree.map((d) => `${d.degree}×${d.count}`).join(' + ')} = ${byDegree.reduce((s, d) => s + d.degree * d.count, 0)}。\n\n联立得 n0 = 1 + Σ(i − 1)·n_i = 1 + ${byDegree.map((d) => `${d.degree - 1}×${d.count}`).join(' + ')} = ${n0}。`,
    }
  })
}

function huffman() {
  const random = rng(seedOf('cs408-ds-huffman'))
  return family(random, 6, (r, index) => {
    const n = r.int(5, 7)
    const weights = r.distinct(n, 1, 30).sort((a, b) => a - b)
    const queue = [...weights]
    let wpl = 0
    const merges = []
    while (queue.length > 1) {
      queue.sort((a, b) => a - b)
      const a = queue.shift()
      const b = queue.shift()
      wpl += a + b
      merges.push(`${a} + ${b} = ${a + b}`)
      queue.push(a + b)
    }
    const sum = weights.reduce((s, w) => s + w, 0)
    const naive = weights.reduce((s, w, i) => s + w * (n - i), 0) // a "chain" tree
    const { options, answer } = shuffleOptions(r, `${wpl}`, [`${sum}`, `${wpl + weights[0]}`, `${wpl - weights[0]}`, `${naive}`, `${wpl + sum}`])
    return {
      ...cs408(`g-cs408-ds-huffman-${nn(index)}`, ['cs408.ds.tree.huffman'], 2),
      stem: `以 ${weights.join('、')} 作为叶子结点的权值构造哈夫曼树，该树的带权路径长度（WPL）为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `每次取当前最小的两个权值合并：${merges.join('；')}。\n\nWPL 等于所有非叶结点（合并结果）的权值之和：${merges.map((m) => m.split('= ')[1]).join(' + ')} = ${wpl}。\n\n也可按“叶子权值 × 路径长度”逐个累加验证。哈夫曼树中有 ${2 * n - 1} 个结点，没有度为 1 的结点。`,
    }
  })
}

function siftDown(a, i, n) {
  // 1-based min-heap sift-down on a 0-based array view
  let k = i
  for (;;) {
    let small = k
    const l = 2 * k
    const rr = 2 * k + 1
    if (l <= n && a[l - 1] < a[small - 1]) small = l
    if (rr <= n && a[rr - 1] < a[small - 1]) small = rr
    if (small === k) return
    ;[a[k - 1], a[small - 1]] = [a[small - 1], a[k - 1]]
    k = small
  }
}

function heap() {
  const random = rng(seedOf('cs408-ds-heap'))
  return family(random, 6, (r, index) => {
    const n = r.int(7, 9)
    const data = r.distinct(n, 10, 99)
    const floyd = [...data]
    for (let i = Math.floor(n / 2); i >= 1; i--) siftDown(floyd, i, n)
    const inserted = []
    for (const x of data) {
      inserted.push(x)
      let k = inserted.length
      while (k > 1 && inserted[Math.floor(k / 2) - 1] > inserted[k - 1]) {
        ;[inserted[Math.floor(k / 2) - 1], inserted[k - 1]] = [inserted[k - 1], inserted[Math.floor(k / 2) - 1]]
        k = Math.floor(k / 2)
      }
    }
    const sorted = [...data].sort((a, b) => a - b)
    const half = [...data]
    siftDown(half, Math.floor(n / 2), n)
    const { options, answer } = shuffleOptions(r, seq(floyd), [seq(inserted), seq(sorted), seq(half)].filter((s) => s !== seq(floyd)).concat([seq([...floyd].reverse())]))
    return {
      ...cs408(`g-cs408-ds-heap-${nn(index)}`, ['cs408.ds.tree.heap', 'cs408.ds.sort.selection'], 3),
      stem: `将关键字序列 ${seq(data)} 用筛选法（从最后一个非叶结点开始自下而上调整）建成小根堆，得到的堆（按数组顺序）为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `n = ${n}，从第 ⌊n/2⌋ = ${Math.floor(n / 2)} 个结点开始，依次向前对每个结点做向下调整（与较小的孩子交换，直到不再小于孩子）。\n\n结果：${seq(floyd)}。\n\n${seq(inserted) === seq(floyd) ? '本例中逐个插入建堆恰好得到相同的结果，但一般两种建堆方法的结果不同，题目说的是筛选法。' : `易错点：逐个插入建堆会得到 ${seq(inserted)}，与筛选法的结果不同；题目说的是筛选法。`}`,
    }
  })
}

// --- 查找 -----------------------------------------------------------------------------

function avlInsert(node, key, log) {
  if (!node) return { key, left: null, right: null, h: 1 }
  if (key < node.key) node.left = avlInsert(node.left, key, log)
  else node.right = avlInsert(node.right, key, log)
  const h = (t) => (t ? t.h : 0)
  const fix = (t) => {
    t.h = 1 + Math.max(h(t.left), h(t.right))
    return t
  }
  fix(node)
  const balance = h(node.left) - h(node.right)
  const rotateRight = (y) => {
    const x = y.left
    y.left = x.right
    x.right = y
    fix(y)
    return fix(x)
  }
  const rotateLeft = (x) => {
    const y = x.right
    x.right = y.left
    y.left = x
    fix(x)
    return fix(y)
  }
  if (balance > 1) {
    if (key < node.left.key) {
      log.push(`插入 ${key} 后在 ${node.key} 处失衡（LL），右旋`)
      return rotateRight(node)
    }
    log.push(`插入 ${key} 后在 ${node.key} 处失衡（LR），先左旋后右旋`)
    node.left = rotateLeft(node.left)
    return rotateRight(node)
  }
  if (balance < -1) {
    if (key > node.right.key) {
      log.push(`插入 ${key} 后在 ${node.key} 处失衡（RR），左旋`)
      return rotateLeft(node)
    }
    log.push(`插入 ${key} 后在 ${node.key} 处失衡（RL），先右旋后左旋`)
    node.right = rotateRight(node.right)
    return rotateLeft(node)
  }
  return node
}

function avl() {
  const random = rng(seedOf('cs408-ds-avl'))
  return family(random, 6, (r, index) => {
    const keys = r.distinct(r.int(6, 8), 1, 60)
    let root = null
    const log = []
    for (const key of keys) root = avlInsert(root, key, log)
    if (log.length < 2) throw new Retry()
    const levelOrder = []
    const queue = [root]
    while (queue.length) {
      const node = queue.shift()
      levelOrder.push(node.key)
      if (node.left) queue.push(node.left)
      if (node.right) queue.push(node.right)
    }
    const askRoot = index % 2 === 1
    if (askRoot) {
      const { options, answer } = shuffleOptions(r, `${root.key}`, [`${keys[0]}`, `${keys[1]}`, `${[...keys].sort((a, b) => a - b)[Math.floor(keys.length / 2)]}`, `${keys[keys.length - 1]}`, `${levelOrder[1]}`])
      return {
        ...cs408(`g-cs408-ds-avl-${nn(index)}`, ['cs408.ds.search.avl'], 3),
        stem: `依次将关键字 ${seq(keys)} 插入一棵初始为空的平衡二叉树（AVL 树），插入完成后树的根结点为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `逐个插入并在失衡时旋转：${log.join('；')}。\n\n最终 AVL 树的层序序列为 ${seq(levelOrder)}，根结点为 ${root.key}。\n\n易错点：每次插入后自插入点向上找第一个失衡结点（平衡因子绝对值为 2），按 LL/RR/LR/RL 四种情况调整。`,
      }
    }
    const { options, answer } = shuffleOptions(r, `${root.h}`, [`${root.h + 1}`, `${root.h - 1}`, `${keys.length}`, `${Math.ceil(Math.log2(keys.length + 1)) === root.h ? root.h + 2 : Math.ceil(Math.log2(keys.length + 1))}`])
    return {
      ...cs408(`g-cs408-ds-avl-${nn(index)}`, ['cs408.ds.search.avl'], 3),
      stem: `依次将关键字 ${seq(keys)} 插入一棵初始为空的平衡二叉树（AVL 树），插入完成后该树的高度为（ ）。（只有根结点的树高度为 1）`,
      type: 'single_choice',
      options,
      answer,
      analysis: `逐个插入并在失衡时旋转：${log.join('；')}。\n\n最终 AVL 树的层序序列为 ${seq(levelOrder)}，高度为 ${root.h}。\n\n若不做平衡调整，按普通二叉排序树插入，树会更高。`,
    }
  })
}

function binarySearch() {
  const random = rng(seedOf('cs408-ds-binary'))
  return family(random, 6, (r, index) => {
    const n = r.int(10, 20)
    const array = r.distinct(n, 1, 99).sort((a, b) => a - b)
    const comparisons = (target) => {
      let low = 1
      let high = n
      let count = 0
      const path = []
      while (low <= high) {
        const mid = Math.floor((low + high) / 2)
        count++
        path.push(array[mid - 1])
        if (array[mid - 1] === target) return { count, path }
        if (array[mid - 1] < target) low = mid + 1
        else high = mid - 1
      }
      return { count, path }
    }
    if (index % 2 === 1) {
      const target = array[r.int(0, n - 1)]
      const { count, path } = comparisons(target)
      const { options, answer } = shuffleOptions(r, `${count}`, [`${count + 1}`, `${Math.max(1, count - 1)}`, `${Math.floor(Math.log2(n)) + 1}` === `${count}` ? `${count + 2}` : `${Math.floor(Math.log2(n)) + 1}`, `${array.indexOf(target) + 1}`])
      return {
        ...cs408(`g-cs408-ds-binary-${nn(index)}`, ['cs408.ds.search.binary'], 2),
        stem: `在有序表 (${seq(array)}) 中用折半查找法查找关键字 ${target}（mid = ⌊(low + high)/2⌋，下标从 1 开始），需要与表中元素比较的次数为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `依次比较的元素为 ${path.join(' → ')}，共 ${count} 次找到 ${target}。`,
      }
    }
    let total = 0
    for (const x of array) total += comparisons(x).count
    const asl = new Q(BigInt(total), BigInt(n))
    const show = (q) => (q.isInt() ? q.toString() : `${q.toString()}`)
    const { options, answer } = shuffleOptions(r, show(asl), [show(new Q(BigInt(total + n), BigInt(n))), show(new Q(BigInt(total), BigInt(n + 1))), show(new Q(BigInt(Math.floor(Math.log2(n)) + 1), 1n)), show(new Q(BigInt(total - 1), BigInt(n)))])
    const levels = []
    for (const x of array) {
      const c = comparisons(x).count
      levels[c] = (levels[c] ?? 0) + 1
    }
    return {
      ...cs408(`g-cs408-ds-binary-${nn(index)}`, ['cs408.ds.search.binary'], 3),
      stem: `对长度为 ${n} 的有序表进行折半查找（mid = ⌊(low + high)/2⌋），在等概率情况下查找成功的平均查找长度为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `画出折半查找判定树：${levels.map((count, depth) => (count ? `第 ${depth} 层 ${count} 个结点` : null)).filter(Boolean).join('，')}。\n\n查找成功时比较次数等于所在层数：ASL = (${levels.map((count, depth) => (count ? `${depth}×${count}` : null)).filter(Boolean).join(' + ')}) / ${n} = ${total}/${n}${asl.isInt() ? '' : ` ≈ ${fmt(asl.toNumber(), 2)}`}。`,
    }
  })
}

function hashing() {
  const random = rng(seedOf('cs408-ds-hash'))
  const PRIMES = [7, 11, 13]
  return family(random, 8, (r, index) => {
    const p = r.pick(PRIMES)
    const m = r.int(p, p + 3)
    const keys = r.distinct(r.int(6, Math.min(9, m - 2)), 1, 99)
    const slots = new Array(m).fill(null)
    const probes = {}
    for (const key of keys) {
      let address = key % p
      let count = 1
      while (slots[address] !== null) {
        address = (address + 1) % m
        count++
      }
      slots[address] = key
      probes[key] = count
    }
    const collisions = Object.values(probes).filter((c) => c > 1).length
    if (collisions < 2) throw new Retry()
    if (index % 2 === 1) {
      const key = keys[r.int(Math.floor(keys.length / 2), keys.length - 1)]
      const address = slots.indexOf(key)
      const { options, answer } = shuffleOptions(r, `${address}`, [`${key % p}`, `${(address + 1) % m}`, `${key % m}`, `${(key % p + probes[key]) % m}`])
      return {
        ...cs408(`g-cs408-ds-hash-${nn(index)}`, ['cs408.ds.search.hash'], 2),
        stem: `散列表长度为 ${m}（地址 0~${m - 1}），散列函数 H(key) = key mod ${p}，用线性探测法（d = 1, 2, 3, …）处理冲突。依次插入关键字 ${seq(keys)} 后，关键字 ${key} 所在的地址为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `逐个插入：${keys.map((k) => `${k} → H = ${k % p}${probes[k] > 1 ? `，冲突 ${probes[k] - 1} 次后放在 ${slots.indexOf(k)}` : ''}`).join('；')}。\n\n${slots.map((v, i) => `${i}:${v ?? '—'}`).join('  ')}\n\n关键字 ${key} 在地址 ${address}。`,
      }
    }
    const total = keys.reduce((s, k) => s + probes[k], 0)
    const asl = new Q(BigInt(total), BigInt(keys.length))
    const { options, answer } = shuffleOptions(r, asl.toString(), [new Q(BigInt(keys.length), BigInt(keys.length)).toString() === asl.toString() ? '2' : '1', new Q(BigInt(total + collisions), BigInt(keys.length)).toString(), new Q(BigInt(total), BigInt(m)).toString(), new Q(BigInt(total), BigInt(p)).toString()])
    return {
      ...cs408(`g-cs408-ds-hash-${nn(index)}`, ['cs408.ds.search.hash'], 3),
      stem: `散列表长度为 ${m}，散列函数 H(key) = key mod ${p}，用线性探测法处理冲突。依次插入关键字 ${seq(keys)}，在等概率情况下查找成功的平均查找长度为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `逐个插入并记录比较次数：\n\n${table(['关键字', ...keys], [['H(key)', ...keys.map((k) => k % p)], ['存放地址', ...keys.map((k) => slots.indexOf(k))], ['比较次数', ...keys.map((k) => probes[k])]])}\n\nASL成功 = (${keys.map((k) => probes[k]).join(' + ')}) / ${keys.length} = ${total}/${keys.length}${asl.toString() === `${total}/${keys.length}` ? '' : ` = ${asl.toString()}`}。\n\n易错点：查找成功的 ASL 除以关键字个数 ${keys.length}，而不是表长或模数。`,
    }
  })
}

function kmp() {
  const random = rng(seedOf('cs408-ds-kmp'))
  return family(random, 6, (r, index) => {
    const m = r.int(6, 8)
    const pattern = Array.from({ length: m }, () => r.pick(['a', 'a', 'b', 'b', 'c'])).join('')
    const p = ' ' + pattern
    const next = new Array(m + 1).fill(0)
    let i = 1
    let j = 0
    next[1] = 0
    while (i < m) {
      if (j === 0 || p[i] === p[j]) {
        i++
        j++
        next[i] = j
      } else j = next[j]
    }
    const nextval = [...next]
    for (let k = 2; k <= m; k++) if (p[k] === p[next[k]]) nextval[k] = nextval[next[k]]
    const correct = next.slice(1)
    const askNextval = index % 3 === 0
    const right = askNextval ? nextval.slice(1) : correct
    const wrongs = [askNextval ? correct : nextval.slice(1), correct.map((v) => v - 1), right.map((v, k) => (k === m - 1 ? v + 1 : v)), right.map((v, k) => (k >= 2 ? Math.max(1, v - 1) : v))]
    const { options, answer } = shuffleOptions(r, right.join(' '), wrongs.map((w) => w.join(' ')))
    return {
      ...cs408(`g-cs408-ds-kmp-${nn(index)}`, ['cs408.ds.search.string'], 3),
      stem: `模式串 P = "${pattern}"，下标从 1 开始，约定 next[1] = 0。按 KMP 算法，P 的 ${askNextval ? 'nextval' : 'next'} 数组依次为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `next[j] = 前 j−1 个字符的最长相等前后缀长度 + 1（j = 1 时为 0）：\n\n${table(['j', ...Array.from({ length: m }, (_, k) => k + 1)], [['P[j]', ...pattern.split('')], ['next[j]', ...correct], ['nextval[j]', ...nextval.slice(1)]])}\n\n${askNextval ? 'nextval：若 P[j] = P[next[j]]，则 nextval[j] = nextval[next[j]]，否则 nextval[j] = next[j]——跳过必然失败的比较。' : '易错点：0 起始的约定会使每个值整体减 1；不要与 nextval 混淆。'}`,
    }
  })
}

// --- 排序 -----------------------------------------------------------------------------

function partition(a) {
  const x = [...a]
  let low = 0
  let high = x.length - 1
  const pivot = x[low]
  while (low < high) {
    while (low < high && x[high] >= pivot) high--
    x[low] = x[high]
    while (low < high && x[low] <= pivot) low++
    x[high] = x[low]
  }
  x[low] = pivot
  return x
}

function quickPass() {
  const random = rng(seedOf('cs408-ds-quick'))
  return family(random, 6, (r, index) => {
    const data = r.distinct(r.int(8, 10), 10, 99)
    const result = partition(data)
    const lomuto = (() => {
      const x = [...data]
      const pivot = x[0]
      let store = 0
      for (let i = 1; i < x.length; i++) {
        if (x[i] < pivot) {
          store++
          ;[x[store], x[i]] = [x[i], x[store]]
        }
      }
      ;[x[0], x[store]] = [x[store], x[0]]
      return x
    })()
    const smaller = data.filter((v) => v < data[0])
    const larger = data.filter((v) => v > data[0])
    const stable = [...smaller, data[0], ...larger]
    if (seq(result) === seq(stable) || seq(lomuto) === seq(result)) throw new Retry()
    const { options, answer } = shuffleOptions(r, seq(result), [seq(lomuto), seq(stable), seq([...data].sort((a, b) => a - b)), seq([...result].reverse())])
    return {
      ...cs408(`g-cs408-ds-quick-${nn(index)}`, ['cs408.ds.sort.exchange'], 3),
      stem: `对关键字序列 (${seq(data)}) 进行快速排序，以第一个元素为枢轴，采用“从两端交替向中间扫描”的划分方法（high 端先向左找小于枢轴的元素，low 端再向右找大于枢轴的元素），第一趟划分后的序列为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `枢轴 pivot = ${data[0]}。high 从右向左找第一个小于 ${data[0]} 的元素移到 low 处；low 从左向右找第一个大于 ${data[0]} 的元素移到 high 处；交替进行直到 low = high，再把枢轴放入该位置。\n\n第一趟结果：${seq(result)}。枢轴 ${data[0]} 已到达最终位置（第 ${result.indexOf(data[0]) + 1} 位），左边都小于它，右边都大于它。\n\n易错点：划分不保持其余元素的相对次序；${seq(stable)} 是“稳定划分”的结果，不是这种算法的输出。`,
    }
  })
}

function shellPass() {
  const random = rng(seedOf('cs408-ds-shell'))
  return family(random, 4, (r, index) => {
    const n = r.int(9, 11)
    const data = r.distinct(n, 10, 99)
    const d = r.pick([3, 4, 5])
    const x = [...data]
    for (let i = d; i < n; i++) {
      const tmp = x[i]
      let j = i - d
      while (j >= 0 && x[j] > tmp) {
        x[j + d] = x[j]
        j -= d
      }
      x[j + d] = tmp
    }
    const wrongD = (dd) => {
      const y = [...data]
      for (let i = dd; i < n; i++) {
        const tmp = y[i]
        let j = i - dd
        while (j >= 0 && y[j] > tmp) {
          y[j + dd] = y[j]
          j -= dd
        }
        y[j + dd] = tmp
      }
      return y
    }
    const { options, answer } = shuffleOptions(r, seq(x), [seq(wrongD(d === 3 ? 4 : 3)), seq(wrongD(1)), seq(wrongD(d + 1)), seq(data)])
    return {
      ...cs408(`g-cs408-ds-shell-${nn(index)}`, ['cs408.ds.sort.insertion'], 2),
      stem: `对序列 (${seq(data)}) 进行希尔排序，第一趟的增量 d = ${d}，第一趟排序结束后的序列为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `增量 ${d} 把序列分成 ${d} 组（下标相差 ${d} 的元素为一组），每组内做直接插入排序：${Array.from({ length: d }, (_, g) => `(${data.filter((_, i) => i % d === g).join(', ')}) → (${x.filter((_, i) => i % d === g).join(', ')})`).join('；')}。\n\n合起来得到 ${seq(x)}。`,
    }
  })
}

function heapSortStep() {
  const random = rng(seedOf('cs408-ds-heapsort'))
  return family(random, 4, (r, index) => {
    const n = r.int(7, 9)
    const data = r.distinct(n, 10, 99)
    const maxSift = (a, i, size) => {
      let k = i
      for (;;) {
        let big = k
        if (2 * k <= size && a[2 * k - 1] > a[big - 1]) big = 2 * k
        if (2 * k + 1 <= size && a[2 * k] > a[big - 1]) big = 2 * k + 1
        if (big === k) return
        ;[a[k - 1], a[big - 1]] = [a[big - 1], a[k - 1]]
        k = big
      }
    }
    const heapArr = [...data]
    for (let i = Math.floor(n / 2); i >= 1; i--) maxSift(heapArr, i, n)
    const after = [...heapArr]
    ;[after[0], after[n - 1]] = [after[n - 1], after[0]]
    maxSift(after, 1, n - 1)
    const noResift = [...heapArr]
    ;[noResift[0], noResift[n - 1]] = [noResift[n - 1], noResift[0]]
    const { options, answer } = shuffleOptions(r, seq(after), [seq(heapArr), seq(noResift), seq([...data].sort((a, b) => b - a)), seq([...after].reverse())])
    return {
      ...cs408(`g-cs408-ds-heapsort-${nn(index)}`, ['cs408.ds.sort.selection', 'cs408.ds.tree.heap'], 3),
      stem: `对序列 (${seq(data)}) 进行升序堆排序：先用筛选法建成大根堆，再将堆顶与最后一个元素交换并对剩余元素重新调整成堆。完成第一次“交换 + 调整”后，序列为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `建成的大根堆为 ${seq(heapArr)}。堆顶 ${heapArr[0]} 与末尾 ${heapArr[n - 1]} 交换得 ${seq(noResift)}，再对前 ${n - 1} 个元素自堆顶向下调整：${seq(after)}。最大值 ${heapArr[0]} 已在最终位置。`,
    }
  })
}

function mergeRadix() {
  const random = rng(seedOf('cs408-ds-merge-radix'))
  return family(random, 4, (r, index) => {
    const n = r.int(8, 10)
    if (index % 2 === 1) {
      const data = r.distinct(n, 10, 99)
      const x = []
      for (let i = 0; i < n; i += 2) x.push(...data.slice(i, i + 2).sort((a, b) => a - b))
      const fours = []
      for (let i = 0; i < n; i += 4) fours.push(...data.slice(i, i + 4).sort((a, b) => a - b))
      const { options, answer } = shuffleOptions(r, seq(x), [seq(fours), seq(data), seq([...data].sort((a, b) => a - b)), seq(x.map((_, i) => x[i ^ 1] ?? x[i]))])
      return {
        ...cs408(`g-cs408-ds-merge-radix-${nn(index)}`, ['cs408.ds.sort.merge-radix'], 2),
        stem: `对序列 (${seq(data)}) 进行二路归并排序（自底向上，先把相邻的两个元素归并），第一趟归并后的结果为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `第一趟把长度为 1 的有序段两两归并成长度为 2 的有序段：${seq(x)}。第二趟再归并成长度为 4 的段：${seq(fours)}。`,
      }
    }
    const data = r.distinct(n, 100, 999)
    const byDigit = (d) => {
      const buckets = Array.from({ length: 10 }, () => [])
      for (const v of data) buckets[Math.floor(v / 10 ** d) % 10].push(v)
      return buckets.flat()
    }
    const first = byDigit(0)
    const { options, answer } = shuffleOptions(r, seq(first), [seq(byDigit(2)), seq(byDigit(1)), seq([...data].sort((a, b) => a - b)), seq([...first].reverse())])
    return {
      ...cs408(`g-cs408-ds-merge-radix-${nn(index)}`, ['cs408.ds.sort.merge-radix'], 2),
      stem: `对序列 (${seq(data)}) 进行最低位优先（LSD）的基数排序，第一趟“分配 + 收集”后的结果为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `第一趟按个位分配到 0~9 号队列（同一队列保持原相对次序），再按 0~9 顺序收集：${seq(first)}。\n\n第二趟按十位、第三趟按百位后即有序。基数排序是稳定的，每趟都必须保持上一趟的相对次序。`,
    }
  })
}

// --- 图 -------------------------------------------------------------------------------

function randomGraph(r, n, extra, directed) {
  const edges = []
  const has = new Set()
  const add = (u, v) => {
    const key = directed ? `${u}-${v}` : `${Math.min(u, v)}-${Math.max(u, v)}`
    if (u === v || has.has(key)) return false
    has.add(key)
    edges.push({ u, v })
    return true
  }
  for (let v = 1; v < n; v++) add(r.int(0, v - 1), v) // spanning backbone
  let guard = 0
  while (edges.length < n - 1 + extra && guard++ < 200) {
    const u = r.int(0, n - 1)
    const v = r.int(0, n - 1)
    add(u, v)
  }
  return edges
}

function dijkstra() {
  const random = rng(seedOf('cs408-ds-dijkstra'))
  return family(random, 6, (r, index) => {
    const n = r.int(5, 6)
    const edges = randomGraph(r, n, r.int(3, 5), true).map((e) => ({ ...e, w: r.int(1, 15) }))
    const dist = new Array(n).fill(Infinity)
    const done = new Array(n).fill(false)
    dist[0] = 0
    const order = []
    for (let round = 0; round < n; round++) {
      let best = -1
      for (let v = 0; v < n; v++) if (!done[v] && (best === -1 || dist[v] < dist[best])) best = v
      if (best === -1 || dist[best] === Infinity) break
      const ties = Array.from({ length: n }, (_, v) => v).filter((v) => !done[v] && v !== best && dist[v] === dist[best])
      if (ties.length > 0) throw new Retry()
      done[best] = true
      order.push(best)
      for (const e of edges) if (e.u === best && dist[best] + e.w < dist[e.v]) dist[e.v] = dist[best] + e.w
    }
    if (order.length < n) throw new Retry()
    const name = (v) => `v${v}`
    const rows = edges.map((e) => [`<${name(e.u)}, ${name(e.v)}>`, e.w])
    if (index % 2 === 1) {
      const target = r.int(2, n - 1)
      const direct = edges.find((e) => e.u === 0 && e.v === target)
      const { options, answer } = shuffleOptions(r, `${dist[target]}`, [`${direct ? direct.w : dist[target] + 3}`, `${dist[target] + 1}`, `${dist[target] - 1}`, `${edges.filter((e) => e.v === target).reduce((m, e) => Math.min(m, e.w), 99)}`].filter((v) => v !== `${dist[target]}` && Number(v) > 0))
      return {
        ...cs408(`g-cs408-ds-dijkstra-${nn(index)}`, ['cs408.ds.graph.shortest-path'], 3),
        stem: `带权有向图的边及权值如下表。用 Dijkstra 算法求从顶点 v0 出发的最短路径，v0 到 ${name(target)} 的最短路径长度为（ ）。\n\n${table(['边', '权值'], rows)}`,
        type: 'single_choice',
        options,
        answer,
        analysis: `Dijkstra 每次从未确定的顶点中选 dist 最小者加入集合 S，并用它更新邻接点：顶点依次确定的顺序为 ${order.map(name).join(' → ')}，最终最短距离 ${order.map((v) => `${name(v)}:${dist[v]}`).join('，')}。\n\nv0 到 ${name(target)} 的最短路径长度为 ${dist[target]}。`,
      }
    }
    const correct = order.slice(1).map(name).join(', ')
    const byIndex = Array.from({ length: n - 1 }, (_, i) => name(i + 1)).join(', ')
    const byDirect = [...order.slice(1)].sort((a, b) => {
      const da = edges.find((e) => e.u === 0 && e.v === a)?.w ?? 99
      const db = edges.find((e) => e.u === 0 && e.v === b)?.w ?? 99
      return da - db || a - b
    }).map(name).join(', ')
    const swapped = [...order.slice(1)]
    ;[swapped[1], swapped[2]] = [swapped[2], swapped[1]]
    const { options, answer } = shuffleOptions(r, correct, [byIndex, byDirect, swapped.map(name).join(', '), [...order.slice(1)].reverse().map(name).join(', ')])
    return {
      ...cs408(`g-cs408-ds-dijkstra-${nn(index)}`, ['cs408.ds.graph.shortest-path'], 3),
      stem: `带权有向图的边及权值如下表。用 Dijkstra 算法求从顶点 v0 出发到其余各顶点的最短路径，其余顶点被确定最短路径（加入集合 S）的先后顺序为（ ）。\n\n${table(['边', '权值'], rows)}`,
      type: 'single_choice',
      options,
      answer,
      analysis: `初始 dist[v0] = 0。每轮选未确定顶点中 dist 最小者加入 S，再用它的出边更新其他顶点：顺序为 ${order.map(name).join(' → ')}，对应最短距离 ${order.map((v) => `${name(v)}:${dist[v]}`).join('，')}。\n\n易错点：不是按“与 v0 直接相连的边权”排序，而是按当前的最短距离逐个确定。`,
    }
  })
}

function mst() {
  const random = rng(seedOf('cs408-ds-mst'))
  return family(random, 6, (r, index) => {
    const n = r.int(5, 6)
    const raw = randomGraph(r, n, r.int(3, 5), false)
    const weights = r.distinct(raw.length, 1, 30)
    const edges = raw.map((e, i) => ({ ...e, w: weights[i] }))
    const sorted = [...edges].sort((a, b) => a.w - b.w)
    const parent = Array.from({ length: n }, (_, i) => i)
    const find = (x) => (parent[x] === x ? x : (parent[x] = find(parent[x])))
    const chosen = []
    const skipped = []
    for (const e of sorted) {
      const a = find(e.u)
      const b = find(e.v)
      if (a === b) skipped.push(e)
      else {
        parent[a] = b
        chosen.push(e)
      }
    }
    const total = chosen.reduce((s, e) => s + e.w, 0)
    const greedy = sorted.slice(0, n - 1).reduce((s, e) => s + e.w, 0)
    if (greedy === total) throw new Retry()
    const name = (v) => String.fromCharCode(65 + v)
    const rows = edges.map((e) => [`(${name(e.u)}, ${name(e.v)})`, e.w])
    const { options, answer } = shuffleOptions(r, `${total}`, [`${greedy}`, `${total + (skipped[0]?.w ?? 1) - chosen[chosen.length - 1].w}`, `${total + 1}`, `${edges.reduce((s, e) => s + e.w, 0) - total}`])
    return {
      ...cs408(`g-cs408-ds-mst-${nn(index)}`, ['cs408.ds.graph.mst'], 2),
      stem: `无向带权连通图的边及权值如下表，其最小生成树的权值之和为（ ）。\n\n${table(['边', '权值'], rows)}`,
      type: 'single_choice',
      options,
      answer,
      analysis: `Kruskal：按权值从小到大考察各边，不形成回路就选入：${sorted.map((e) => `(${name(e.u)}, ${name(e.v)}) ${e.w} ${chosen.includes(e) ? '选' : '成环舍弃'}`).slice(0, chosen.length + skipped.length).join('；')}。\n\n选中 ${n - 1} 条边：${chosen.map((e) => `(${name(e.u)}, ${name(e.v)})`).join('、')}，权值和 = ${chosen.map((e) => e.w).join(' + ')} = ${total}。\n\n易错点：直接取最小的 ${n - 1} 条边（和为 ${greedy}）会包含回路，不是生成树。用 Prim 算法从任一顶点出发得到的权值和相同。`,
    }
  })
}

function topo() {
  const random = rng(seedOf('cs408-ds-topo'))
  return family(random, 5, (r, index) => {
    const n = 6
    const order = r.shuffle([0, 1, 2, 3, 4, 5])
    const edges = []
    for (let i = 0; i < n; i++) {
      for (let j = i + 1; j < n; j++) if (r.chance(0.35)) edges.push({ u: order[i], v: order[j] })
    }
    if (edges.length < 5) throw new Retry()
    const valid = (seqList) => {
      const at = Object.fromEntries(seqList.map((v, i) => [v, i]))
      return edges.every((e) => at[e.u] < at[e.v])
    }
    const validOrders = new Set()
    let guard = 0
    while (validOrders.size < 3 && guard++ < 500) {
      const indeg = new Array(n).fill(0)
      edges.forEach((e) => indeg[e.v]++)
      const out = []
      const ready = Array.from({ length: n }, (_, v) => v).filter((v) => indeg[v] === 0)
      while (ready.length) {
        const k = r.int(0, ready.length - 1)
        const v = ready.splice(k, 1)[0]
        out.push(v)
        for (const e of edges) if (e.u === v && --indeg[e.v] === 0) ready.push(e.v)
      }
      validOrders.add(out.join(','))
    }
    let bad = null
    for (let i = 0; i < 200 && !bad; i++) {
      const candidate = r.shuffle([0, 1, 2, 3, 4, 5])
      if (!valid(candidate)) bad = candidate
    }
    if (validOrders.size < 3 || !bad) throw new Retry()
    const name = (v) => `v${v}`
    const show = (list) => list.map(name).join(', ')
    const broken = edges.find((e) => bad.indexOf(e.u) > bad.indexOf(e.v))
    const { options, answer } = shuffleOptions(r, show(bad), [...validOrders].map((s) => show(s.split(',').map(Number))))
    return {
      ...cs408(`g-cs408-ds-topo-${nn(index)}`, ['cs408.ds.graph.topo'], 2),
      stem: `有向无环图 G 的边集为 {${edges.map((e) => `<${name(e.u)}, ${name(e.v)}>`).join(', ')}}。下列序列中，不是 G 的拓扑序列的是（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `拓扑序列要求每条边 <u, v> 中 u 都排在 v 之前。选项 ${show(bad)} 中，${name(broken.v)} 排在 ${name(broken.u)} 之前，违反了边 <${name(broken.u)}, ${name(broken.v)}>。\n\n其余选项都可由“反复删除入度为 0 的顶点”得到；拓扑序列一般不唯一。`,
    }
  })
}

function criticalPath() {
  const random = rng(seedOf('cs408-ds-critical'))
  return family(random, 4, (r, index) => {
    const n = 6
    const edges = []
    for (let v = 1; v < n; v++) edges.push({ u: r.int(Math.max(0, v - 2), v - 1), v, w: r.int(1, 9) })
    for (let k = 0; k < 4; k++) {
      const u = r.int(0, n - 2)
      const v = r.int(u + 1, n - 1)
      if (!edges.some((e) => e.u === u && e.v === v)) edges.push({ u, v, w: r.int(1, 9) })
    }
    const ve = new Array(n).fill(0)
    for (let v = 0; v < n; v++) for (const e of edges) if (e.v === v) ve[v] = Math.max(ve[v], ve[e.u] + e.w)
    // the sink must be reachable from everything: every vertex except the sink needs an out-edge
    for (let v = 0; v < n - 1; v++) if (!edges.some((e) => e.u === v)) throw new Retry()
    const length = ve[n - 1]
    const vl = new Array(n).fill(length)
    for (let v = n - 1; v >= 0; v--) for (const e of edges) if (e.u === v) vl[v] = Math.min(vl[v], vl[e.v] - e.w)
    const critical = edges.filter((e) => ve[e.u] === vl[e.v] - e.w)
    const shortest = (() => {
      const d = new Array(n).fill(Infinity)
      d[0] = 0
      for (let v = 0; v < n; v++) for (const e of edges) if (e.v === v) d[v] = Math.min(d[v], d[e.u] + e.w)
      return d[n - 1]
    })()
    const name = (v) => `v${v + 1}`
    const total = edges.reduce((s, e) => s + e.w, 0)
    const { options, answer } = shuffleOptions(r, `${length}`, [`${shortest}`, `${total}`, `${length - 1}`, `${length + 2}`])
    return {
      ...cs408(`g-cs408-ds-critical-${nn(index)}`, ['cs408.ds.graph.critical-path'], 3),
      stem: `某工程的 AOE 网中，顶点 v1 为源点、${name(n - 1)} 为汇点，各活动（边）及其持续时间如下表。完成整个工程至少需要的时间为（ ）。\n\n${table(['活动', '持续时间'], edges.map((e) => [`<${name(e.u)}, ${name(e.v)}>`, e.w]))}`,
      type: 'single_choice',
      options,
      answer,
      analysis: `按拓扑序求事件最早发生时间 ve：${ve.map((t, v) => `${name(v)}:${t}`).join('，')}；工程最短工期等于源点到汇点的最长路径长度 ve(${name(n - 1)}) = ${length}。\n\n再逆序求最迟发生时间 vl：${vl.map((t, v) => `${name(v)}:${t}`).join('，')}；关键活动（e = l）为 ${critical.map((e) => `<${name(e.u)}, ${name(e.v)}>`).join('、')}。\n\n易错点：工期是最长路径而不是最短路径（${shortest}）。`,
    }
  })
}

// --- 数组与特殊矩阵、B 树 -----------------------------------------------------------------

function matrix() {
  const random = rng(seedOf('cs408-ds-matrix'))
  return family(random, 6, (r, index) => {
    if (index % 3 === 0) {
      const rows = r.int(5, 20)
      const cols = r.int(5, 20)
      const size = r.pick([2, 4, 8])
      const base = r.int(10, 99) * 10
      const i = r.int(1, rows - 1)
      const j = r.int(1, cols - 1)
      const rowMajor = r.chance(0.5)
      const correct = base + (rowMajor ? i * cols + j : j * rows + i) * size
      const other = base + (rowMajor ? j * rows + i : i * cols + j) * size
      const { options, answer } = shuffleOptions(r, `${correct}`, [`${other}`, `${base + (rowMajor ? i * cols + j : j * rows + i)}`, `${correct + size}`, `${base + ((i + 1) * cols + (j + 1)) * size}`])
      return {
        ...cs408(`g-cs408-ds-matrix-${nn(index)}`, ['cs408.ds.stack.array'], 1),
        stem: `二维数组 A[${rows}][${cols}]（行下标 0~${rows - 1}，列下标 0~${cols - 1}）按${rowMajor ? '行' : '列'}优先存储，每个元素占 ${size} 个存储单元，A[0][0] 的地址为 ${base}。则 A[${i}][${j}] 的地址为（ ）。`,
        type: 'single_choice',
        options,
        answer,
        analysis: rowMajor
          ? `按行优先，A[${i}][${j}] 前面有 ${i} 整行（每行 ${cols} 个）加本行 ${j} 个，共 ${i} × ${cols} + ${j} = ${i * cols + j} 个元素。地址 = ${base} + ${i * cols + j} × ${size} = ${correct}。`
          : `按列优先，A[${i}][${j}] 前面有 ${j} 整列（每列 ${rows} 个）加本列 ${i} 个，共 ${j} × ${rows} + ${i} = ${j * rows + i} 个元素。地址 = ${base} + ${j * rows + i} × ${size} = ${correct}。`,
      }
    }
    const n = r.int(8, 30)
    let i = r.int(1, n)
    let j = r.int(1, n)
    const upper = i < j
    const zeroBased = r.chance(0.5)
    const [a, b] = i >= j ? [i, j] : [j, i]
    const k = (a * (a - 1)) / 2 + b - (zeroBased ? 1 : 0)
    const wrongs = [(b * (b - 1)) / 2 + a - (zeroBased ? 1 : 0), (a * (a + 1)) / 2 + b - (zeroBased ? 1 : 0), k + (zeroBased ? 1 : -1), (a * (a - 1)) / 2 + b + 1]
    const { options, answer } = shuffleOptions(r, `${k}`, wrongs.map(String))
    return {
      ...cs408(`g-cs408-ds-matrix-${nn(index)}`, ['cs408.ds.stack.matrix'], 2),
      stem: `将 ${n} 阶对称矩阵 A（行、列下标均从 1 开始）的下三角部分（含主对角线）按行优先压缩存储到一维数组 B 中，B 的下标从 ${zeroBased ? 0 : 1} 开始。元素 a[${i}][${j}] 在 B 中的下标为（ ）。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `${upper ? `a[${i}][${j}] 位于上三角，由对称性 a[${i}][${j}] = a[${j}][${i}]，改求 a[${a}][${b}]。` : ''}a[${a}][${b}]（${a} ≥ ${b}）之前有第 1~${a - 1} 行共 1 + 2 + … + ${a - 1} = ${(a * (a - 1)) / 2} 个元素，本行前面还有 ${b - 1} 个，所以它是第 ${(a * (a - 1)) / 2 + b} 个元素。\n\nB 下标从 ${zeroBased ? 0 : 1} 开始，下标为 ${k}。\n\n易错点：先判断在上三角还是下三角；注意数组下标的起点。`,
    }
  })
}

function btree() {
  const random = rng(seedOf('cs408-ds-btree'))
  return family(random, 4, (r, index) => {
    const m = r.pick([3, 4, 5])
    const h = r.int(3, 5)
    const half = Math.ceil(m / 2)
    if (index % 2 === 1) {
      const min = 2 * half ** (h - 1) - 1
      const { options, answer } = shuffleOptions(r, `${min}`, [`${half ** h - 1}`, `${2 * half ** h - 1}`, `${m ** (h - 1)}`, `${min + 1}`, `${2 * half ** (h - 1)}`])
      return {
        ...cs408(`g-cs408-ds-btree-${nn(index)}`, ['cs408.ds.search.btree'], 3),
        stem: `一棵高度为 ${h} 的 ${m} 阶 B 树（不含叶结点层，即失败结点层），至少包含（ ）个关键字。`,
        type: 'single_choice',
        options,
        answer,
        analysis: `关键字最少时：根只有 1 个关键字、2 个孩子；其余每个非根结点只有 ⌈m/2⌉ − 1 = ${half - 1} 个关键字、${half} 个孩子。\n\n第 2~${h} 层结点数依次为 2、2×${half}、…、2×${half}^${h - 2}；共有 n 个关键字时失败结点数为 n + 1，且至少为 2×${half}^${h - 1}，所以 n ≥ 2×${half}^${h - 1} − 1 = ${min}。`,
      }
    }
    const max = m ** h - 1
    const { options, answer } = shuffleOptions(r, `${max}`, [`${m ** h}`, `${(m - 1) * h}`, `${m ** (h + 1) - 1}`, `${max - (m - 1)}`])
    return {
      ...cs408(`g-cs408-ds-btree-${nn(index)}`, ['cs408.ds.search.btree'], 2),
      stem: `一棵高度为 ${h} 的 ${m} 阶 B 树（不含失败结点层），最多包含（ ）个关键字。`,
      type: 'single_choice',
      options,
      answer,
      analysis: `关键字最多时每个结点都有 m − 1 = ${m - 1} 个关键字、m = ${m} 个孩子：结点总数 1 + ${m} + … + ${m}^${h - 1} = (${m}^${h} − 1)/(${m} − 1)，关键字数 = (${m} − 1) × 结点数 = ${m}^${h} − 1 = ${max}。`,
    }
  })
}

export default {
  id: 'gen-cs408-ds',
  file: 'cs408/gen-cs408-ds.json',
  pack: {
    pack: 'gen-cs408-ds',
    title: '408 数据结构 · 计算专项（生成）',
    version: 1,
    description: '出栈序列、循环队列、遍历序列还原、二叉树性质、哈夫曼树、堆、AVL、折半查找、散列、KMP、快排划分、希尔、堆排序、归并与基数、Dijkstra、最小生成树、拓扑排序、关键路径、数组与对称矩阵、B 树——由算法运行得出答案。',
    generator: 'content/generators/cs408-ds.mjs',
  },
  build() {
    return [
      ...stackSequences(),
      ...circularQueue(),
      ...traversal(),
      ...treeProperties(),
      ...huffman(),
      ...heap(),
      ...avl(),
      ...binarySearch(),
      ...hashing(),
      ...kmp(),
      ...quickPass(),
      ...shellPass(),
      ...heapSortStep(),
      ...mergeRadix(),
      ...dijkstra(),
      ...mst(),
      ...topo(),
      ...criticalPath(),
      ...matrix(),
      ...btree(),
    ]
  },
}
