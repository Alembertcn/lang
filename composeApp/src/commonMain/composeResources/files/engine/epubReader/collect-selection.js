/*
 * Copyright © Hugecore Information Technology (Guangzhou) Co.,Ltd. All rights reserved
 * @Date: 2023-01-30 17:26:34
 * @Author: Jing
 * @LastEditors: rin
 * @LastEditTime: 2023-02-21 18:35:28
 * @FilePath: /js-for-native-mojidict-app/A_collectSelection/collect-selection.js
 * @Description:
 */
// #region defined
const sentenceMap = new Map()
const highlightMap = new Map()
let currentEnableScroll = true

let highlighter = new Highlighter({
  exceptSelectors: ['pre', 'code'],
})
// highlighter.on(Highlighter.event.CLICK, function (data) {
//     let sentenceId = highlightMap.get(data.id)
//     errorTips('click hl: ' + sentenceId)
//     // sentenceId && req(Api.showMenu, sentenceId)
// })

// #endregion

// #region test case
function debughl () {
  let param = collectSentence()
  if (!param) return

  let underlinePosition = param.split('$')[0]
  collectSuccessCallBack(underlinePosition, '19873673', new Date().getTime())
}

function debugremove () {
  cancelCollectSuccessCallBack('19873673')
}

// #endregion

// #region export
function initManySentence (sentenceList) {
  for (let index = 0, len = sentenceList.length; index < len; index++) {
    let s = sentenceList[index]
    collectSuccessCallBack(s.underlinePosition, s.id, s.createTime)
  }
}

function createSentenceRange() {
  const selectSentenceNodes = document.querySelectorAll(".on-click-word")
  if (selectSentenceNodes && selectSentenceNodes.length) {
    try {
      console.log("createSentenceRange", selectSentenceNodes)
      window.getSelection().removeAllRanges()
      const selection = window.getSelection()
      const textNodes = [];
      for (let i = 0; i < selectSentenceNodes.length; i++) {
        const node = selectSentenceNodes[i];
        textNodes.push(node.childNodes[0]);
      }
      console.log("createSentenceRange", textNodes)
      const range = document.createRange()
      range.setStart(textNodes[0], 0)
      range.setEnd(textNodes[textNodes.length - 1], textNodes[textNodes.length - 1].length)
      selection.addRange(range)
      console.log("createSentenceRange", range)
      return range
    } catch (error) {
      console.log(error)
      return null
    }
  } else {
    return null
  }
}

function getTextNodes(node) {
  let textNodes = [];

  $(node).contents().each(function() {
    if (this.nodeType === 3) {
      textNodes.push(this);
    } else if (this.nodeType === 1) {
      const childTextNodes = getTextNodes(this);
      textNodes = textNodes.concat(childTextNodes);
    }
  });

  return textNodes;
}

function collectSentence (source = 'article') {
  let range = getDomRange() || createSentenceRange()
  if (!range) return false

  let underlinePosition = getPosition()

  if (source == 'book' && EPUBcfi) {
    let position = underlinePosition.split('#')
    if (position.length != 4) {
      return
    }
    let startMOJiId = position[0]
    let startOffset = position[1]
    let startContainer = getTextChildByOffset(document.querySelector(`[moji-id="${startMOJiId}"]`), startOffset)
    // let startContainer = window.getSelection().focusNode ? { node: window.getSelection().focusNode } : getTextChildByOffset(document.querySelector(`[moji-id="${startMOJiId}"]`), startOffset)

    console.log("startContainer", window.getSelection())

    let locationCfi = getNodeCfi(startContainer.node)
    return underlinePosition + '$' + locationCfi
  } else if (source == 'article') {
    return underlinePosition
  } else {
    return false
  }
}

function collectSuccessCallBack (underlinePosition, sentenceId, sentenceCreateTime) {
  try {
    let position = underlinePosition.split('#')
    if (position.length != 4) {
      return
    }

    let startMOJiId = position[0]
    let endMOJiId = position[2]
    let startOffset = position[1]
    let endOffset = position[3]
    let startContainer = getTextChildByOffset(document.querySelector(`[moji-id="${startMOJiId}"]`), startOffset)
    let endContainer = getTextChildByOffset(document.querySelector(`[moji-id="${endMOJiId}"]`), endOffset)

    setDomRange(startContainer.node, startContainer.offset, endContainer.node, endContainer.offset)

    let hlId = setHightLight()
    hlId && saveSentenceCache(sentenceId, hlId, sentenceCreateTime)

    setTimeout(() => {
      removeSelection()
    }, 200)
  } catch (error) {
    console.error(error)
  }
}

function cancelCollectSuccessCallBack (sentenceId) {
  $('.on-click-word').contents().unwrap()
  let s = sentenceMap.get(sentenceId)
  s && removeHighlight(s.hlId)
  s && removeSentenceCache(sentenceId, s.hlId)
}
window.getHighLightSentenceId = getHighLightSentenceId
function getHighLightSentenceId () {
  let domRange = getDomRange()
  let sentenceRange

  if (domRange) {
    range = domRange
  } else {
    sentenceRange = createSentenceRange()
    range = sentenceRange
  }

  console.log("getHighLightSentenceId", range)
  if (!range) return null

  let hlId_s = getHlId(range.startContainer)
  let hlId_e = getHlId(range.endContainer)

  if (hlId_s == hlId_e) {
    let sentenceId = highlightMap.get(hlId_s)
    sentenceRange && window.getSelection().removeAllRanges()
    return sentenceId
  } else {
    // window.getSelection().removeAllRanges()
    return null
  }
}

// #endregion

// #region helper
// function registerLongPressEvent (container) {
//     let startTime = 0

//     container.ontouchstart = function () {
//         startPressCallBack()
//         container.ontouchend = finishPressCallBack
//         container.onmousedown = null
//         container.onmouseup = null
//     }
//     container.onmousedown = function () {
//         startPressCallBack()
//         container.onmouseup = finishPressCallBack
//     }

//     function startPressCallBack () {
//         startTime = new Date().getTime()
//     }


//     function finishPressCallBack (e) {
//         let endTime = new Date().getTime()
//         if (endTime - startTime > 300) {
//             let target = e.target || e.changedTouches[0].target
//             let hlId = getHlId(target)
//             if (hlId) {
//                 e.stopPropagation()
//                 e.preventDefault()
//                 let sentenceId = highlightMap.get(hlId)
//                 try {
//                     sentenceId && req(Api.showMenu, sentenceId)
//                 } catch (error) {

//                 }
//                 setTimeout(() => {
//                     removeSelection()
//                 }, 200)
//             }

//         }
//     }
// }

function getHlId (el) {
  if (el.nodeType != document.TEXT_NODE && (el.hasAttribute('data-highlight-id') || el.hasAttribute('data-highlight-id-extra'))) {
    let extra = el.getAttribute('data-highlight-id-extra')
    return (extra && extra.split(';').reverse()[0] || el.getAttribute('data-highlight-id'))
  } else if (document.body.contains(el.parentNode)) {
    return getHlId(el.parentNode)
  } else {
    return false
  }
}

function saveSentenceCache (sId, hlId, sentenceCreateTime) {
  sentenceMap.set(sId, {
    hlId,
    createTime: sentenceCreateTime
  })
  highlightMap.set(hlId, sId)
}

function removeSentenceCache (sId, hlId) {
  sId && sentenceMap.delete(sId)
  hlId && highlightMap.delete(hlId)
}

function setHightLight () {
  let range = getDomRange()
  if (!range) return false

  let highData = highlighter.fromRange(range)
  // console.log('创建例句', highData.text)
  return highData.id
}
function removeHighlight (id) {
  highlighter.remove(id)
}

function getDomRange () {
  const selection = window.getSelection()
  if (selection.isCollapsed) {
    return null
  }
  return selection.getRangeAt(0)
}
function setDomRange (nodeA, offsetA, nodeB, offsetB) {
  removeSelection()

  let selection = window.getSelection()
  let range = document.createRange()
  range.setStart(nodeA, offsetA)
  range.setEnd(nodeB, offsetB)
  selection.addRange(range)
  return range
}

function removeSelection () {
  window.getSelection().removeAllRanges()
}

/**
 * @description: 处理选区，限制选区在段落内
 * @return {*}
 */
function handlerSelection () {
  let range = getDomRange()
  if (!range) return false

  const { startContainer, startOffset, endContainer, endOffset } = range
  const { startParent, endParent } = getRangeParentNode(range)
  if (startParent && endParent && endParent == startParent) {
    // console.log('是一个范围内的数据', range)
    return startParent
  } else if (startParent && endParent != startParent) {
    // console.log('不是一个范围内的数据，修改选区')
    let endNode = findLastTextNode(startParent.lastChild)
    setDomRange(startContainer, startOffset, endNode, endNode.nodeValue.length)
    return startContainer
  } else if (!startParent && endParent) {
    let startNode = findFirstTextNode(endParent.firstChild)
    setDomRange(startNode, 0, endContainer, endOffset)
    return startNode
  } else {
    // 首尾元素不符合规范
    return false
  }

  /**
   * @description: 找到节点末尾的文本节点
   * @return {*}
   * @param {*} node
   */
  function findLastTextNode (node) {
    if (node.nodeType === document.TEXT_NODE) {
      return node
    } else {
      return findLastTextNode(node.lastChild)
    }
  }

  function findFirstTextNode (node) {
    if (node.nodeType === document.TEXT_NODE) {
      return node
    } else {
      return findFirstTextNode(node.firstChild)
    }
  }
}

/**
 * @description: 获取选区开始与结束节点的P/DIV祖先节点
 * @return {*}
 * @param {*} range
 */
function getRangeParentNode (range) {
  if (range) {
    return {
      startParent: getBlockNode(range.startContainer),
      endParent: getBlockNode(range.endContainer)
    }
  } else {
    return {
      startParent: null,
      endParent: null
    }
  }
}
/**
 * @description: 获取为P/DIV的父标签
 * @return {*}
 * @param {*} node
 */
function getBlockNode (node, returnVal = null) {
  if (!returnVal && node.nodeType != document.TEXT_NODE && node.getAttribute('moji-id')) {
    returnVal = node
  }
  if (isSource(node.className)) {
    return null
  } else if (node.parentNode.nodeName == 'BODY') {
    return returnVal
  } else {
    return getBlockNode(node.parentNode, returnVal)
  }

  function isSource (className) {
    return /moji-article-source-item | moji-article-voter | moji-article-link-item/g.test(className)
  }
}

function getPosition () {
  let range = getDomRange()
  let { startContainer, endContainer } = range
  let startParentNode = getMojiIdNode(range.startContainer)
  //   console.log('***', startParentNode)
  let endParentNode = getMojiIdNode(range.endContainer)
  //   console.log('***', endParentNode)

  let startPosition = startParentNode.getAttribute('moji-id') + '#' + (getTextPreOffset(startParentNode, startContainer) + range.startOffset)
  let endPosition = endParentNode.getAttribute('moji-id') + '#' + (getTextPreOffset(endParentNode, endContainer) + range.endOffset)

  let startPositionArr = startPosition.split("#")
  if (startPositionArr && startPositionArr[1] == 0) {
    startPositionArr[1] = "1"
    startPosition = startPositionArr.join("#")
  }

  console.log(startPosition + '#' + endPosition)

  return startPosition + '#' + endPosition
}

function getMojiIdNode (node) {
  if (node.nodeType == Node.TEXT_NODE || !node.getAttribute('moji-id')) {
    return getMojiIdNode(node.parentNode)
  } else {
    // console.dir('getMojiIdNode', node)
    return node
  }
}
function getTextPreOffset (root, text) {
  // console.log('getTextPreOffset', root)
  // console.log('getTextPreOffset', text)
  const nodeStack = []
  let curNode = root
  let offset = 0
  while (curNode) {
    const children = curNode.childNodes
    for (let i = children.length - 1; i >= 0; i--) {
      nodeStack.push(children[i])
    }
    if (curNode.nodeType === document.TEXT_NODE && curNode !== text) {
      offset += curNode.textContent.length
    } else if (curNode.nodeType === document.TEXT_NODE) {
      break
    }
    curNode = nodeStack.pop()
  }
  // console.log('getTextPreOffset', offset)
  return offset
}
function getTextChildByOffset (parent, offset) {
  const nodeStack = []
  let curNode = parent
  let curOffset = 0
  let startOffset = 0
  while (curNode) {
    const children = curNode.childNodes
    for (let i = children.length - 1; i >= 0; i--) {
      nodeStack.push(children[i])
    }
    if (curNode.nodeType === document.TEXT_NODE) {
      startOffset = offset - curOffset
      curOffset += curNode.textContent.length
      if (curOffset >= offset) {
        break
      }
    }
    curNode = nodeStack.pop()
  }
  if (!curNode) {
    curNode = parent
  }
  return { node: curNode, offset: startOffset }
}
// #endregion

//#region onselectionchange
function listenerOnselectionchange () {

  document.onselectionchange = function () {
    let coord = getSelectionArea()
    coord && window.webkit.messageHandlers.MOJiReadingArticleDetailVCChangeSelectedCoordinate.postMessage(JSON.stringify(coord))

    //TODO 阅读器：处理跨页划选句子
    // if (EPUBcfi) {
    //     let range = getDomRange()
    //     if (range) {
    //         let endContainer = range.endContainer

    //         var bodyStyle = document.body.currentStyle || window.getComputedStyle(document.body)
    //         var columnWidth = parseInt(bodyStyle.columnWidth, 10)

    //         if (coord.width > columnWidth) {
    //             setScrollEnable(true)
    //             let pageIndex = getPageIndexByNodeOrRange(endContainer)
    //             if (currentPageIndex != pageIndex) {
    //                 scrollToPageIndex(pageIndex)
    //             }
    //         } else {
    //             setScrollEnable(false)
    //         }
    //     } else {
    //         setScrollEnable(true)
    //     }
    // }
  }
}

function setScrollEnable (allow) {
  if (currentEnableScroll == value) return

  if (allow) {
    document.body.style.overflowX = 'auto'
    document.body.style.overflowY = 'hidden'
    req(Api.setScrollEnable, true)
  } else {
    document.body.style.overflowX = 'hidden'
    document.body.style.overflowY = 'hidden'
    req(Api.setScrollEnable, false)
  }

  currentEnableScroll = allow
}

function getSelectionArea () {
  const selection = window.getSelection()
  if (!selection.isCollapsed) {
    return selection.getRangeAt(0).getBoundingClientRect()
  }
  return null
}
//#endregion
