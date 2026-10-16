/**
 * 英语（一）· 原创样题 — the texts the bank lacked for one whole paper.
 *
 * With core-english1 (one reading text, the two writing tasks) these fill every
 * section of the paper once: a cloze text of 20 blanks, three more reading
 * texts of 5 questions, a 七选五 text and a translation text of 5 sentences.
 * Every text was written for this pack; nothing is taken from a past paper.
 *
 * In-place numbering is relative to the text: a cloze or 七选五 text marks its
 * blanks **[1]** … and a translation text its sentences **(1)** …; question n
 * of the text names its marker in the stem (PackValidator checks both). The
 * paper renumbers questions as printed, so "Blank [3]" of the cloze is question
 * 3 of a whole paper and the translation's "(1)" is question 46.
 *
 * Answer letters were planned per section — five each of A–D across the cloze
 * and across the four reading texts — so no pattern gives an answer away.
 */
import { SAMPLE_SOURCE, md } from '../lib/author.mjs'

function group(key, section, title, passage) {
  return { key, subject: 'english1', section, title, passage, source: SAMPLE_SOURCE }
}

function choice(key, section, groupKey, points, difficulty, stem, options, answer, analysis) {
  return {
    key, subject: 'english1', section, type: 'single_choice', group: groupKey, points, difficulty,
    source: SAMPLE_SOURCE, stem, options, answer, analysis,
  }
}

// --- 完形填空 ---------------------------------------------------------------------------

const CLOZE = 'english1-sample-cloze-g1'

const clozeText = md`
  *Directions: Read the following text. Choose the best word(s) for each numbered blank.*

  Students who take notes by hand may remember lectures better than those who type, a finding that has **[1]** a lively debate among teachers. In a typical study of this kind, two groups of students watch the same lectures. One group takes notes on laptops, **[2]** the other uses pen and paper. When **[3]** later, both groups recall facts about equally well, but the handwriters tend to do better on questions that **[4]** them to explain ideas in their own words.

  The usual explanation is **[5]**. Because most people type much faster than they write, laptop users tend to **[6]** the speaker's words almost verbatim. Handwriters, unable to keep up, are **[7]** to listen, select and summarize — and that extra mental effort is **[8]** what helps information stick. In other words, the slowness of handwriting turns **[9]** to be an advantage rather than a weakness.

  Not everyone is convinced. **[10]** attempts to repeat such studies have produced weaker and less consistent results, and critics point out that the effect may depend **[11]** how the notes are used. Students who review their notes before an exam, **[12]**, benefit from having a complete record, which laptops make easier to **[13]**.

  What seems clear is that the tool matters less than the habit. A student who types but **[14]** to rephrase each point can gain the same benefit as one who writes by hand. **[15]**, a handwriter who mechanically copies every word gains little. The key is to process ideas **[16]** than merely record them.

  For teachers, the lesson is not to ban laptops but to **[17]** active note-taking: asking students to summarize a lecture in three sentences, for instance, or to **[18]** new ideas with what they already know. Such practices **[19]** nothing and work with any tool. After all, the purpose of taking notes was never to produce a perfect transcript; it was to **[20]**.`

const LOGIC = 'english1.cloze.skills.logic'
const WORD = 'english1.cloze.skills.word'
const COLLOCATION = 'english1.cloze.skills.collocation'

/** [points, difficulty, options, answer, analysis] for blanks 1–20, in text order. */
const blanks = [
  [[LOGIC, WORD], 3, ['ended', 'avoided', 'sparked', 'settled'], 'C',
    '空后 a lively debate（热烈的讨论）与第三段 Not everyone is convinced 呼应：这一发现"引发"了争论，spark a debate 是常见搭配。ended、settled（结束、平息）与下文仍有争议相矛盾；avoided 不合逻辑。'],
  [[LOGIC], 2, ['while', 'unless', 'because', 'so'], 'A',
    '前后两组学生的做法形成对比（laptops / pen and paper），while 表对比。unless（除非）、because（因为）、so（所以）都不表示对比关系。'],
  [[COLLOCATION], 2, ['testing', 'to test', 'having tested', 'tested'], 'D',
    'When tested later 即 When they are tested later：主语 both groups 与 test 是被动关系，用过去分词。testing 表主动（"去测试别人"），to test、having tested 结构不当。'],
  [[WORD, COLLOCATION], 2, ['prevent', 'require', 'forbid', 'excuse'], 'B',
    'questions that require them to explain ideas in their own words：要求他们用自己的话解释观点的题目。require sb. to do 是固定用法；prevent 后接 from doing；forbid 语义相反；excuse 不用于此义。'],
  [[LOGIC, WORD], 2, ['simple', 'hidden', 'doubtful', 'accidental'], 'A',
    '下文直接给出解释（打字快 → 逐字照录；手写慢 → 被迫概括），且作者认同这一解释，说明它"简单明了"。hidden、accidental 与语境无关；doubtful 与本段肯定的语气矛盾。'],
  [[WORD], 2, ['ignore', 'question', 'copy', 'forget'], 'C',
    'almost verbatim（几乎逐字地）是线索：用电脑的学生倾向于"照抄"讲者的原话。ignore、forget、question 都与 verbatim 矛盾。'],
  [[WORD], 2, ['free', 'forbidden', 'reluctant', 'forced'], 'D',
    '手写者 unable to keep up（跟不上），所以"被迫"去听、筛选和概括：be forced to do。free 语义相反；forbidden、reluctant 不合逻辑。'],
  [[WORD], 2, ['hardly', 'exactly', 'rarely', 'seldom'], 'B',
    'that extra mental effort is exactly what helps information stick：正是这份额外的思考让信息留在记忆里。hardly、rarely、seldom 都带否定意味，与上下文矛盾。'],
  [[COLLOCATION], 1, ['up', 'down', 'out', 'over'], 'C',
    'turn out to be（结果是、原来是）是固定搭配：手写的慢原来是优点而不是缺点。'],
  [[LOGIC], 2, ['Later', 'Earlier', 'Rare', 'Unknown'], 'A',
    'attempts to repeat such studies（重复此类研究的尝试）必然发生在原研究之后，用 Later。Earlier 违背时间逻辑；Rare、Unknown 与 have produced weaker results 搭配不当。'],
  [[COLLOCATION], 1, ['in', 'at', 'with', 'on'], 'D',
    'depend on（取决于）是固定搭配。'],
  [[LOGIC], 2, ['in short', 'for example', 'as a result', 'in contrast'], 'B',
    '本句是批评者观点（效果取决于笔记如何使用）的例证：考前复习笔记的学生受益于完整的记录。in short（总之）、as a result（因此）、in contrast（相比之下）都不是"举例"关系。'],
  [[WORD], 2, ['produce', 'destroy', 'hide', 'avoid'], 'A',
    'a complete record, which laptops make easier to produce：完整的记录用电脑更容易"做出来"。destroy、hide、avoid 都与语境矛盾。'],
  [[WORD, LOGIC], 3, ['hurries', 'refuses', 'pauses', 'forgets'], 'C',
    'types but pauses to rephrase each point：打字（快）但停下来改写每个要点，but 表转折，pause to do 指"停下来去做"。refuses、forgets 与后文"获得同样的益处"矛盾；hurries 与"改写需要放慢"相悖。'],
  [[LOGIC], 2, ['Similarly', 'Therefore', 'Moreover', 'Conversely'], 'D',
    '前句说打字也能获益，本句说手写而机械照抄则收益甚微，两种情况恰好相反，用 Conversely（反过来说）。Similarly、Therefore、Moreover 都不表示相反关系。'],
  [[COLLOCATION], 1, ['better', 'rather', 'less', 'other'], 'B',
    'rather than（而不是）是固定搭配：process ideas rather than merely record them。'],
  [[WORD, LOGIC], 2, ['encourage', 'discourage', 'replace', 'punish'], 'A',
    'not to ban laptops but to encourage active note-taking：not … but … 前后对照——不是禁用电脑，而是鼓励主动记笔记。discourage 语义相反；replace、punish 不合逻辑。'],
  [[WORD], 2, ['confuse', 'trade', 'connect', 'hide'], 'C',
    'connect A with B（把新知识与已有知识联系起来）是主动学习的典型做法。confuse A with B（混淆）与"有益的做法"相反；trade、hide 不合逻辑。'],
  [[WORD, COLLOCATION], 2, ['save', 'earn', 'lose', 'cost'], 'D',
    'cost nothing（不花一分钱）与 work with any tool（适用于任何工具）并列，强调这些做法简单易行。save、earn、lose nothing 搭配不当。'],
  [[LOGIC], 2, ['copy', 'understand', 'impress', 'publish'], 'B',
    '呼应全文主旨：记笔记的目的从来不是产出完美的逐字稿，而是"理解"。copy 正是作者否定的做法；impress、publish 与主题无关。'],
]

const cloze = blanks.map(([points, difficulty, options, answer, analysis], index) =>
  choice(`english1-sample-cloze-${101 + index}`, 'english1.cloze', CLOZE, points, difficulty,
    `Blank **[${index + 1}]**`, options, answer, analysis))

// --- 阅读理解 Part A --------------------------------------------------------------------

const DETAIL = 'english1.reading.types.detail'
const INFERENCE = 'english1.reading.types.inference'
const MAIN_IDEA = 'english1.reading.types.main-idea'
const ATTITUDE = 'english1.reading.types.attitude'

const BOREDOM = 'english1-sample-reading-g2'
const MUSEUMS = 'english1-sample-reading-g3'
const REPAIR = 'english1-sample-reading-g4'

const reading = (key, groupKey, points, difficulty, stem, options, answer, analysis) =>
  choice(key, 'english1.reading-a', groupKey, points, difficulty, stem, options, answer, analysis)

const boredomText = md`
  Few experiences are as universally disliked as boredom. We treat it as a problem to be solved, and modern life offers endless solutions: a phone in every pocket, a stream of videos that never ends, a podcast for every commute. It is now possible to go through an entire day without a single empty minute. Yet a number of psychologists have begun to ask whether something valuable is lost when boredom disappears.

  Their argument starts from what boredom actually is. Boredom, they suggest, is not simply the absence of stimulation but a signal — an uncomfortable feeling that tells us our current activity is failing to engage us. Like hunger, it is unpleasant precisely so that we will act on it. The question is how we act. Reaching for a phone silences the signal without answering it; we feel better, but we have learned nothing about what we actually want to do.

  There is also some evidence that boredom can feed creativity. In experiments, people who first completed a dull task, such as copying numbers from a phone book, later came up with more ideas on a creative test than people who had skipped the dull task. One explanation is that a bored mind tends to wander, and mind-wandering allows us to make unexpected connections between distant ideas. If every idle moment is filled, the mind seldom gets the chance.

  None of this means boredom is always good. Chronic boredom, the kind experienced by people stuck in jobs or situations that offer no meaning, is linked to poorer well-being, and nobody is suggesting that schools should become deliberately dull. The point is narrower: short, ordinary stretches of boredom — waiting for a bus, sitting in a quiet room — may be worth keeping rather than eliminating.

  That is harder than it sounds. Researchers who have asked people to sit alone with their thoughts for a few minutes have found that many find the experience surprisingly unpleasant. Perhaps the first step, then, is simply to notice the reflex. The next time boredom arrives, we might wait a moment before silencing it and ask what it is trying to tell us.`

const museumsText = md`
  When museums began putting high-resolution images of their collections online, some curators worried that they were giving away their main attraction. If anyone could study a famous painting on a laptop, zooming in until individual brushstrokes filled the screen, why would they travel across a city — or an ocean — to stand in front of it?

  So far, the fear has proved largely misplaced. Museums that have opened their digital archives generally report no fall in attendance, and some report the opposite. A digital image, it seems, works less like a substitute than like an advertisement. Seeing a picture online can turn vague interest into a specific desire to see the real thing, in the same way that hearing a song on the radio can make people want to attend a concert.

  Part of the explanation lies in what an original offers that a copy cannot. A painting has a physical scale that a screen cannot reproduce; a sculpture changes as the viewer walks around it; even the surface of an old manuscript carries traces of the hands that held it. Visitors also come for the occasion itself: the quiet of a gallery, the company of friends, the sense of having made an effort to be there.

  Digital collections nevertheless change what museums are for. For students who cannot afford to travel, and for researchers who need to compare objects held in different countries, online access is not a poor imitation of a visit but the only way in. A scholar can now examine dozens of related works side by side, something no single museum visit could allow. In this sense, digitization extends the museum's public far beyond those who walk through its doors.

  The real challenge may be financial rather than cultural. Photographing and cataloguing a collection to a high standard is slow and expensive, and the work never truly ends, since files must be maintained as technology changes. Museums that treat digitization as a one-time project risk building archives that quietly decay. Those that succeed tend to budget for it as a permanent responsibility, like conservation or security.`

const repairText = md`
  For much of the twentieth century, repairing things was an ordinary part of life. Shoes were resoled, radios were taken to the repair shop, and a broken chair was glued rather than thrown away. Today, for many products, repair has become the exception. A cracked phone screen or a faulty washing machine is often cheaper to replace than to fix — when it can be fixed at all.

  Manufacturers offer several reasons for this. Modern devices, they argue, are thinner, lighter and more reliable precisely because their parts are glued together or sealed, and opening them up can be dangerous or can compromise water resistance. There is some truth in this. But critics point out that design choices also serve commercial interests: a product that cannot be repaired must eventually be replaced, and spare parts, repair manuals and diagnostic tools are often kept away from independent repairers.

  A growing movement is pushing back. Campaigners argue that consumers should have a "right to repair" — that is, access to parts, tools and information at a fair price. In several places, lawmakers have begun to listen, introducing rules that require manufacturers to make spare parts available for a number of years or to design certain products so that batteries can be replaced. Volunteer "repair cafés", where people bring broken items to be fixed for free, have spread to many cities.

  The environmental argument is strong. Making a new device consumes far more energy and raw material than repairing an old one, and electronic waste is among the fastest-growing kinds of rubbish. Yet the movement is not only about the environment. For many supporters, repair is also a question of ownership: if you cannot open, understand or fix something you have bought, in what sense do you really own it?

  Repair will not solve every problem. Some products really are better replaced, and repair takes skills that many people have lost. But the movement has already changed the conversation. The question is no longer whether a product works when it is new, but how long — and on whose terms — it can be kept working.`

const readings = [
  reading('english1-sample-reading-201', BOREDOM, [DETAIL], 2,
    'According to Paragraph 2, boredom is compared to hunger because both ______.',
    ['are caused by a lack of stimulation', 'can be relieved by using a phone', 'are unpleasant in order to make us act',
      'tell us what we actually want to do'],
    'C',
    md`
      定位第二段：Like hunger, it is unpleasant precisely so that we will act on it。无聊像饥饿一样让人不适，目的是促使我们采取行动，C 是同义改写。

      A 与原文 not simply the absence of stimulation 相反；B 是作者批评的做法，且与饥饿无关；D 原文说拿起手机会让我们 learned nothing about what we actually want to do，无聊只提示当前的活动不够吸引人。`),
  reading('english1-sample-reading-202', BOREDOM, [INFERENCE], 2,
    'The author suggests that reaching for a phone when bored ______.',
    ['answers the signal that boredom sends', 'is the most effective way to regain focus', 'helps us find out what we really want',
      'relieves the feeling but ignores its message'],
    'D',
    md`
      第二段末：Reaching for a phone silences the signal without answering it; we feel better, but we have learned nothing…。D"缓解了感受却忽视了它传达的信息"是同义改写。

      A 与 without answering it 相反；B 原文未提及；C 与 learned nothing about what we actually want to do 相反。`),
  reading('english1-sample-reading-203', BOREDOM, [DETAIL], 2,
    'The experiment mentioned in Paragraph 3 shows that people who did a dull task first ______.',
    ['produced more ideas on a later creative test', 'found the creative test more boring',
      'remembered more numbers from the phone book', 'performed worse than those who skipped it'],
    'A',
    md`
      第三段：people who first completed a dull task … later came up with more ideas on a creative test than people who had skipped the dull task。A 正确。

      B、C 原文未提及；D 与原文相反。`),
  reading('english1-sample-reading-204', BOREDOM, [INFERENCE, ATTITUDE], 3,
    'Which of the following would the author most probably agree with?',
    ['Schools should make lessons deliberately dull.', "Chronic boredom benefits people's well-being.",
      'Boredom should be eliminated whenever possible.', 'Brief everyday boredom may be worth preserving.'],
    'D',
    md`
      第四段：short, ordinary stretches of boredom … may be worth keeping rather than eliminating。D 是同义改写。

      A 与 nobody is suggesting that schools should become deliberately dull 相反；B 原文说长期的无聊与较差的幸福感相关；C 与 worth keeping rather than eliminating 相反。`),
  reading('english1-sample-reading-205', BOREDOM, [MAIN_IDEA], 2,
    'What is the text mainly about?',
    ['Why ordinary boredom may have value', 'Why phones are making people more creative',
      'How to cure chronic boredom at work', 'How psychologists measure creativity'],
    'A',
    md`
      全文结构：提出问题（无聊消失时是否失去了什么）→ 无聊是一种信号 → 无聊可能促进创造力 → 限定（并非所有无聊都好）→ 建议留住短暂的日常无聊。主旨是"日常的无聊可能有价值"。

      B 与原文相反（手机让无聊消失）；C 只对应第四段的一句话；D 是第三段的细节，以偏概全。`),

  reading('english1-sample-reading-301', MUSEUMS, [DETAIL], 1,
    'Some curators were worried that putting images online would ______.',
    ['reduce people\'s reasons to visit museums', 'damage the original paintings', 'make museum websites too expensive',
      'confuse visitors about famous paintings'],
    'A',
    md`
      第一段：curators worried that they were giving away their main attraction… why would they travel… to stand in front of it? 担心网上的图片会让人不再有理由去博物馆，A 正确。

      B、C、D 原文均未提及。`),
  reading('english1-sample-reading-302', MUSEUMS, [INFERENCE], 2,
    'The comparison with "a song on the radio" (Paragraph 2) suggests that digital images ______.',
    ['are of lower quality than the originals', 'are mostly enjoyed by young people',
      'can increase people\'s desire to see the originals', 'should be shared without permission'],
    'C',
    md`
      第二段：A digital image … works less like a substitute than like an advertisement。在网上看到图片会把模糊的兴趣变成想看真品的具体愿望，就像在电台听到一首歌会让人想去听演唱会。类比说明数字图像能激发看原作的欲望，C 正确。

      A 虽然第三段暗示原作有屏幕无法复制之处，但不是这个类比的用意；B、D 原文未提及。`),
  reading('english1-sample-reading-303', MUSEUMS, [DETAIL], 2,
    'Which of the following is NOT mentioned as something an original offers?',
    ['A physical scale that screens cannot show', 'A changing view as one walks around it',
      'Traces left by people who handled it', 'Information unavailable in any catalogue'],
    'D',
    md`
      第三段列举了原作独有的东西：画作的物理尺寸（A）、雕塑随观者走动而变化（B）、古老手稿表面留下的手的痕迹（C）。D（任何目录中都查不到的信息）原文没有提到。

      NOT / EXCEPT 题要逐项回到原文核对，被提到的三项都能找到出处。`),
  reading('english1-sample-reading-304', MUSEUMS, [DETAIL, INFERENCE], 3,
    'According to Paragraph 4, for researchers who compare objects held in different countries, online access is ______.',
    ['a convenient but inferior substitute', 'the only practical way to study the works', 'less useful than a single museum visit',
      'mainly a way to attract new visitors'],
    'B',
    md`
      第四段：online access is not a poor imitation of a visit but the only way in；A scholar can now examine dozens of related works side by side, something no single museum visit could allow。B 正确。

      A 与 not a poor imitation 相反；C 与 something no single museum visit could allow 相反；D 是第二段关于普通观众的内容，不是研究者的情况。`),
  reading('english1-sample-reading-305', MUSEUMS, [DETAIL], 2,
    'The author believes that successful digitization requires museums to ______.',
    ['photograph their collections only once', 'stop spending money on conservation', 'fund it as an ongoing responsibility',
      'limit online access to researchers'],
    'C',
    md`
      末段：Those that succeed tend to budget for it as a permanent responsibility, like conservation or security。C 是同义改写。

      A 与 Museums that treat digitization as a one-time project risk building archives that quietly decay 相反；B 曲解了类比对象 conservation；D 原文未提及。`),

  reading('english1-sample-reading-401', REPAIR, [DETAIL], 1,
    'According to Paragraph 1, many products today ______.',
    ['are repaired more often than in the past', 'last longer than products of the past',
      'are always cheaper to fix than to replace', 'are often cheaper to replace than to fix'],
    'D',
    md`
      第一段末：A cracked phone screen or a faulty washing machine is often cheaper to replace than to fix。D 是原句改写。

      A 与 repair has become the exception 相反；B 原文未提及；C 与原文相反，且 always 过于绝对。`),
  reading('english1-sample-reading-402', REPAIR, [ATTITUDE, INFERENCE], 3,
    'By saying "There is some truth in this" (Paragraph 2), the author ______.',
    ['partly accepts the manufacturers\' reasons', 'fully supports the manufacturers\' design choices',
      'rejects the critics\' commercial argument', 'doubts that modern devices are reliable'],
    'A',
    md`
      some truth（有一定道理）表示部分认可制造商的理由，紧接着 But critics point out… 引出批评者的观点。A 正确。

      B 的 fully 过度；C 与作者随后转述并展开批评者的论证不符；D 原文没有质疑设备的可靠性。`),
  reading('english1-sample-reading-403', REPAIR, [DETAIL], 2,
    'Which of the following is a measure mentioned in Paragraph 3?',
    ['Banning the sale of devices that cannot be opened', 'Requiring consumers to repair their own devices',
      'Requiring spare parts to be available for some years', 'Paying volunteers to run repair cafés'],
    'C',
    md`
      第三段：rules that require manufacturers to make spare parts available for a number of years。C 正确。

      A 原文没有"禁止销售"；B 的要求对象是制造商而不是消费者；D 原文是 Volunteer "repair cafés"——志愿者运营、免费修理，并非付钱给志愿者。`),
  reading('english1-sample-reading-404', REPAIR, [INFERENCE], 2,
    'The question "in what sense do you really own it?" (Paragraph 4) suggests that repair is linked to ______.',
    ['environmental protection', 'the price of new products', 'the safety of electronic devices', 'the idea of ownership'],
    'D',
    md`
      第四段：Yet the movement is not only about the environment. For many supporters, repair is also a question of ownership。设问强调"所有权"，D 正确。

      A 是前文的环保论点，作者用 not only 转向新的论点；B、C 不是此处讨论的内容。`),
  reading('english1-sample-reading-405', REPAIR, [ATTITUDE], 2,
    "The author's attitude toward the right-to-repair movement can best be described as ______.",
    ['supportive but realistic', 'strongly critical', 'completely indifferent', 'blindly enthusiastic'],
    'A',
    md`
      末段：Repair will not solve every problem（承认局限）… But the movement has already changed the conversation（肯定作用）。作者总体支持而不盲目，A 正确。

      B、C 与全文态度不符；D 与作者承认其局限相矛盾。`),
]

// --- 阅读理解 Part B（新题型 · 七选五）---------------------------------------------------

const FEEDBACK = 'english1-sample-newtypes-g1'
const SEVEN_FIVE = 'english1.new-types.formats.seven-five'

const feedbackText = md`
  *Directions: Read the following text and choose the most suitable sentence from the list [A]–[G] for each numbered blank. There are two extra choices which do not fit in any of the blanks.*

  Most of us have received feedback that left us more confused than before: a manager's vague "good job", or a page of comments that seemed to criticize everything at once. Giving feedback that actually helps is a skill, and like most skills it can be learned.

  **[1]** Feedback delivered weeks after an event loses much of its value, because the person may no longer remember the details of what they did. A short conversation the same day is usually worth more than a long written report a month later.

  Be specific about behavior, not personality. Saying "you are disorganized" invites defensiveness and offers no direction. **[2]** The second version describes something the person can see and change.

  **[3]** A list of twenty problems is overwhelming, and the most important point is likely to be lost among the minor ones. Choosing the one or two changes that would make the biggest difference shows that you have thought about what matters.

  Feedback should also be a conversation rather than a verdict. Before offering your own view, ask the other person how they think things went. **[4]** And when they have already noticed the problem themselves, your role becomes one of support rather than criticism.

  Finally, remember that praise is feedback too. Telling someone precisely what worked — "the example in your second paragraph made the argument clear" — teaches them what to repeat. **[5]** People who hear only about their mistakes may conclude that nothing they do is right.`

/** The seven sentences, [A]–[G], offered for every blank. */
const feedbackOptions = [
  'Timing matters more than most people realize.',
  'Focus on a small number of priorities.',
  'By contrast, "the report was missing two of the figures the client asked for" points to a concrete problem.',
  'Their answer often reveals what they already understand and where they need help.',
  'Praise also keeps people willing to listen to criticism.',
  'Written feedback is always more reliable than spoken feedback.',
  'Most employees prefer to receive feedback from their colleagues rather than their managers.',
]

const DISTRACTORS = '干扰项：[F]（书面反馈总比口头反馈可靠）与第二段"当天的简短谈话胜过一个月后的书面报告"相矛盾；[G]（员工更愿意接受同事的反馈）文中没有讨论。'

const sevenFive = [
  ['A', '本段讲反馈的时机：几周后的反馈价值大减，当天的简短谈话胜过一个月后的书面报告。[A]（时机比多数人意识到的更重要）概括段意，作段首主题句。'],
  ['C', '前句给出负面的例子 "you are disorganized"，后句 The second version describes something… 说明空处应是"第二种说法"。[C] 以 By contrast 引出一个具体描述问题的例子，衔接严密。'],
  ['B', '空后说列出二十个问题会让人不知所措，应选择一两个最重要的改变。[B]（聚焦少数优先事项）是本段的主题句。'],
  ['D', '前句建议先问对方觉得事情进展如何，后句 And when they have already noticed the problem themselves… [D]（对方的回答往往揭示他们已经明白什么、哪里需要帮助）承上启下。'],
  ['E', '前句说具体的表扬告诉对方哪些做法要保持，后句说只听到错误的人可能觉得自己一无是处。[E]（表扬还能让人愿意听取批评）与前后两句都衔接。'],
].map(([answer, analysis], index) =>
  choice(`english1-sample-newtypes-${101 + index}`, 'english1.reading-b', FEEDBACK, [SEVEN_FIVE], 3,
    `Blank **[${index + 1}]**`, feedbackOptions, answer, `${analysis}\n\n${DISTRACTORS}`))

// --- 阅读理解 Part C（翻译）---------------------------------------------------------------

const LIBRARIES = 'english1-sample-translation-g1'

const librariesText = md`
  *Directions: Read the following text carefully and then translate the numbered sentences into Chinese.*

  Public libraries are sometimes described as relics of a world before the internet. If information is available on every phone, the argument goes, why should cities spend money on buildings full of books? **(1)** *This argument mistakes what libraries have always provided: not merely access to information, but a public space where anyone may stay without being expected to buy anything.*

  In many neighborhoods, the library is the only indoor place that is free, warm and open to all. Students come to study, job seekers come to use computers and printers, and elderly residents come for company as much as for books. **(2)** *For people who have no quiet place at home, the reading room may be the difference between finishing a course and giving it up.*

  Libraries have also changed with their users. Many now lend laptops, run classes on digital skills and help residents fill in online forms for public services. **(3)** *Far from being made obsolete by technology, they have become one of the places where people learn to use it.*

  None of this comes free. Opening hours, trained staff and safe buildings all cost money, and libraries compete for funding with schools, hospitals and roads. **(4)** *When budgets are cut, the damage is easy to overlook, because those who depend most on libraries are often the people least able to make their voices heard.*

  Supporters argue that a library should be judged not by how many books it lends but by how many lives it touches. **(5)** *That is harder to measure, but it may be the more honest test of whether a city is spending its money well.*`

/** [sentence, reference translation, scoring points, analysis] for sentences (1)–(5). */
const sentences = [
  ['This argument mistakes what libraries have always provided: not merely access to information, but a public space where anyone may stay without being expected to buy anything.',
    '这种观点误解了图书馆一直以来所提供的东西：它不仅是获取信息的途径，更是一个任何人都可以在此停留、而不会被要求消费的公共空间。',
    ['mistakes what … 译为"误解了……"，宾语从句 what libraries have always provided 译为"图书馆一直以来所提供的东西"（0.5 分）',
      'not merely … but … 译出递进关系"不仅……更是……"（0.5 分）',
      'access to information 译为"获取信息的途径"，不直译为"进入信息"（0.5 分）',
      '定语从句 where anyone may stay without being expected to buy anything 前置或拆译，被动结构译成"不会被要求消费"等通顺说法（0.5 分）'],
    '冒号后是对 what libraries have always provided 的解释，译文用"它不仅……更是……"另起一句更通顺。being expected to buy anything 是被动结构，按汉语习惯译为"被要求消费""非得买东西"。'],
  ['For people who have no quiet place at home, the reading room may be the difference between finishing a course and giving it up.',
    '对于家里没有安静地方的人来说，阅览室可能决定了他们是能完成学业还是半途而废。',
    ['For people who have no quiet place at home 译为"对于家里没有安静地方的人来说"，定语从句前置（0.5 分）',
      'the reading room 译为"阅览室"（0.5 分）',
      'may be the difference between A and B 意译为"决定了是 A 还是 B""关系到……"，不直译为"是……之间的区别"（0.5 分）',
      'finishing a course and giving it up 译为"完成学业（课程）"与"放弃（半途而废）"（0.5 分）'],
    'be the difference between A and B 是英语常见的表达，直译"是 A 与 B 之间的区别"不通，应意译为"决定了是 A 还是 B"。'],
  ['Far from being made obsolete by technology, they have become one of the places where people learn to use it.',
    '图书馆非但没有被技术淘汰，反而成了人们学习使用技术的地方之一。',
    ['Far from being made obsolete by technology 译为"非但没有被技术淘汰"，far from 的否定含义译出（1 分）',
      'have become one of the places where people learn to use it 译为"成了人们学习使用……的地方之一"（0.5 分）',
      'they 与 it 的指代分别译为"图书馆"和"技术"（0.5 分）'],
    'far from doing 表示"远非、非但不"，是本句的得分关键；与 they have become… 连用时译成"非但没有……反而……"。代词 they、it 要还原为名词，否则译文指代不清。'],
  ['When budgets are cut, the damage is easy to overlook, because those who depend most on libraries are often the people least able to make their voices heard.',
    '预算一旦削减，造成的损害很容易被忽视，因为最依赖图书馆的人，往往也是最无力表达自身诉求的人。',
    ['When budgets are cut 译为"预算一旦（被）削减"（0.5 分）',
      'the damage is easy to overlook 译为"损害很容易被忽视"——主动形式表被动含义（0.5 分）',
      'because 引导的原因从句译出因果关系（0.5 分）',
      'those who depend most on libraries … the people least able to make their voices heard 两个最高级对照译出，make their voices heard 意译为"表达自身诉求""发出自己的声音"（0.5 分）'],
    'be easy to overlook 是"不定式主动表被动"的结构，译为"容易被忽视"。句末 most 与 least 两个最高级构成对照，译文要保留"最……的人，往往也是最……的人"的对称。'],
  ['That is harder to measure, but it may be the more honest test of whether a city is spending its money well.',
    '这一点更难衡量，但它或许是检验一座城市是否把钱花得恰当的更为真实的标准。',
    ['That is harder to measure 译为"这一点更难衡量"，That 指代上句的 how many lives it touches（0.5 分）',
      'the more honest test 译为"更为真实（可靠）的标准"，不直译为"更诚实的测试"（0.5 分）',
      'of whether a city is spending its money well 宾语从句译为"检验一座城市是否把钱花得恰当"（0.5 分）',
      '全句通顺，转折关系 but 译出（0.5 分）'],
    'honest 修饰 test 时意为"真实反映情况的"，译"更诚实的测试"是常见的直译失分。of whether … 是 test 的内容，汉语中前置为定语。'],
]

const translations = sentences.map(([sentence, reference, scoring, analysis], index) => ({
  key: `english1-sample-translation-${101 + index}`,
  subject: 'english1',
  section: 'english1.translation',
  type: 'open',
  group: LIBRARIES,
  points: ['english1.translation.skills.understanding', 'english1.translation.skills.techniques'],
  difficulty: 3,
  source: SAMPLE_SOURCE,
  stem: `将第 **(${index + 1})** 句译成中文：\n\n> ${sentence}`,
  answer: `**参考译文**：${reference}\n\n**得分点**（共 2 分）：\n\n${scoring.map((line, i) => `${i + 1}. ${line}`).join('\n')}`,
  analysis,
}))

export default {
  id: 'sample-english1',
  file: 'english1/sample-english1.json',
  pack: {
    pack: 'sample-english1',
    title: '英语（一）原创样题 · 题型验证',
    version: 1,
    description: '原创样题，非真题：完形填空 1 篇（20 空）、阅读理解 Part A 3 篇（各 5 题）、新题型七选五 1 篇、翻译 1 篇（5 句），与核心题库的 1 篇阅读和两篇写作合成一套原创样卷，用于验证英语（一）全部题型的在线模考：文章与题目分栏、按篇整取、客观题自动评分、翻译与写作对照参考答案自评。',
    generator: 'content/packs/sample-english1.mjs',
  },
  build: () => ({
    groups: [
      group(CLOZE, 'english1.cloze', 'Notes by hand or by keyboard', clozeText),
      group(BOREDOM, 'english1.reading-a', 'In defense of boredom', boredomText),
      group(MUSEUMS, 'english1.reading-a', 'Museums in the digital age', museumsText),
      group(REPAIR, 'english1.reading-a', 'The return of repair', repairText),
      group(FEEDBACK, 'english1.reading-b', 'Feedback that helps', feedbackText),
      group(LIBRARIES, 'english1.translation', 'Why cities still need libraries', librariesText),
    ],
    questions: [...cloze, ...readings, ...sevenFive, ...translations],
    papers: [
      {
        key: 'english1-sample-paper-01',
        subject: 'english1',
        kind: 'mock',
        title: '英语（一）原创样卷 · 题型验证（非真题）',
        sections: [
          { code: 'english1.cloze', questions: cloze.map((q) => q.key) },
          {
            code: 'english1.reading-a',
            questions: [
              'english1-reading-001', 'english1-reading-002', 'english1-reading-003', 'english1-reading-004',
              'english1-reading-005',
              ...readings.map((q) => q.key),
            ],
          },
          { code: 'english1.reading-b', questions: sevenFive.map((q) => q.key) },
          { code: 'english1.translation', questions: translations.map((q) => q.key) },
          { code: 'english1.writing-a', questions: ['english1-writing-001'] },
          { code: 'english1.writing-b', questions: ['english1-writing-002'] },
        ],
      },
    ],
  }),
}
