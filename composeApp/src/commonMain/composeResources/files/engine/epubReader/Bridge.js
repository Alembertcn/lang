/*
 * Copyright © Hugecore Information Technology (Guangzhou) Co.,Ltd. All rights reserved
 * @Date: 2023-08-14 10:39:55
 * @Author:
 * @LastEditors: huang
 * @LastEditTime: 2023-11-27 14:42:31
 * @FilePath: \hybrid-release\mojiread\epubReader\common\Bridge.js
 * @Description:  Bridge.js
 */
// What is CFI ?
// The CFI class implements an EPUB 3 Content Fragment Identifier.
// A CFI is similar in principle to a DOMRange: it identifies a particular location within a publication.

// const Direction = Object.freeze({
//   VERTICAL: 'VERTICAL',
//   HORIZONTAL: 'HORIZONTAL',
// })

// const DisplayUnit = Object.freeze({
//   PX: 'PX',
//   DP: 'DP',
//   CSS_PX: 'CSS_PX',
// })
// MOJI主题
const MOJiTheme = {
  LIGHT: 'theme-light',
  DARK: 'theme-dark',
  CUSTOM: 'theme-customized',
}

const Api = {
  onLoaded: 'onJsLoaded',
  clickWord: 'clickMOJiArticleContent',
  selectSentence: 'selectMOJiArticleSentence',
  analysisParagraph: 'analysisMOJiArticleParagraph',
  // storeLastReadCfi: 'storeLastReadCfi',
  setCurrentInnerPage: 'setCurrentInnerPage',
  setHorizontalPageCount: 'setHorizontalPageCount',
  setScrollEnable: 'setScrollEnable',
}

const NotationMarkFilter = '.moji-toolkit-org, ruby, .moji-notation'

let scrollWidth
let horizontalInterval
const horizontalIntervalPeriod = 100
let horizontalIntervalCounter = 0
let horizontalPageCalCounter = 0
const horizontalIntervalLimit = 10

let viewportRect
let intersectNode

let isIOS = false
let isAndroid = false
const isFirstLoad = true
const withNotation = true
let currentCFI = ''
let currentPageIndex = 0
let currentPageCount = 0

// #region export
/**
 * @description: entry
 * @return {*}
 * @param { String } detailsInfo json string
 * @param { String } platform ios | android
 * @param { String } lastCfi string
 */
function Main (detailsInfo, platform = '', lastCfi = '', sentenceListJson = '') {
  isIOS = platform == 'ios'
  isAndroid = platform == 'android'

  initHorizontalDirection()

  const info = JSON.parse(detailsInfo)
  window.info = info
  console.log(info, 'info')
  const {
    analysisBgColor,
    analysisColor,
    contentColor,
    themeName = MOJiTheme.LIGHT,
    fontFamily,
    fontSize = 1,
    analysisParagraphButton = true,
  } = info

  if (isIOS) {
    document.documentElement.setAttribute("platform", "ios")
  } else if (isAndroid) {
    document.documentElement.setAttribute("platform", "android")
  }

  MOJiArticleDetailSetAnalysisParagraphButton(analysisParagraphButton)
  analysisBgColor && analysisColor && contentColor && MOJiArticleDetailDidSetThemeNew(analysisBgColor,
    analysisColor,
    contentColor)
  MOJiArticleDetailDidSetTheme(themeName)
  MOJiArticleDetailSetFontSize(fontSize, false)
  setFontFamily(fontFamily, false)
  try {
    const sentenceList =
      (sentenceListJson && JSON.parse(sentenceListJson)) || []
    initManySentence(sentenceList) // in collect-selection.js
  } catch (error) { }

  if (lastCfi) {
    currentCFI = lastCfi
    scrollToCfi(currentCFI)
  }
  setBodyOpacity(1)

  window.onresize = function () {
    onMounted()
  }
  isIOS && listenerOnselectionchange() // in collect-selection.js
  registerImageClickAction() // in MOJiRegisterImageClick.js
}

/**
 * @description: 翻页后计算阅读进度(如果首元素跨多个页面，将记录元素开始位置)
 * @return { String } cfi
 * @param { String } currentViewportRect
 */
function computeLastReadCfi () {
  viewportRect = getCurrentDOMRect()
  const node =
    getFirstVisibleNode(document.body) || intersectNode || document.body
  const cfi = getNodeCfi(node)

  viewportRect = null
  intersectNode = null

  // req(Api.storeLastReadCfi, cfi)

  currentCFI = cfi
  return cfi
}

function getNodeCfi (node) {
  let cfi
  if (node.nodeType === Node.TEXT_NODE) {
    cfi = EPUBcfi.Generator.generateCharacterOffsetCFIComponent(node, 0)
  } else {
    cfi = EPUBcfi.Generator.generateElementCFIComponent(node)
  }

  cfi = EPUBcfi.Generator.generateCompleteCFI('/0!', cfi)

  return cfi
}

// #endregion

// Class manipulation
function hasClass (ele, cls) {
  return !!ele.className.match(new RegExp(`(\\s|^)${cls}(\\s|$)`))
}

function addClass (ele, cls) {
  if (!hasClass(ele, cls)) ele.className += ` ${cls}`
}

function removeClass (ele, cls) {
  if (hasClass(ele, cls)) {
    const reg = new RegExp(`(\\s|^)${cls}(\\s|$)`)
    ele.className = ele.className.replace(reg, ' ')
  }
}

/* * *  初始化页面 start * * */
function initHorizontalDirection () {
  horizontalIntervalCounter = 0
  horizontalPageCalCounter = 0
  // 预初始化页面：设置每页大小样式
  preInitHorizontalDirection()
  // 获取当前页数&初始化微调页面滚动距离
  postInitHorizontalDirection()

  horizontalInterval && clearInterval(horizontalInterval)
  horizontalInterval = setInterval(horizontalRecheck, horizontalIntervalPeriod)

  autoZoomCover()
}
// 确认页面滚动页面是否达到预定位置（ps:页面可能未加载完全所以需多次确认）
function horizontalRecheck () {
  // if (window.scrollWidth != document.documentElement.scrollWidth) {
  // Rare condition
  // This might happen when document.documentElement.scrollWidth gives incorrect value
  // when the webview is busy re-drawing contents.
  // console.log("-> horizontalIntervalCounter = " + horizontalIntervalCounter);
  console.warn(
    `-> scrollWidth changed from ${window.scrollWidth} to ${document.documentElement.scrollWidth}`
  )
  horizontalIntervalCounter++

  const pageCount = postInitHorizontalDirection()
  if (pageCount != currentPageCount) {
    currentPageCount = pageCount
  } else if (horizontalIntervalCounter >= horizontalIntervalLimit) {
    clearInterval(horizontalInterval)
    req(Api.setHorizontalPageCount, currentPageCount)
  } else {
    horizontalPageCalCounter++
    if (horizontalPageCalCounter > 6) {
      clearInterval(horizontalInterval)
      req(Api.setHorizontalPageCount, currentPageCount)
    }
  }
  // }
}
// 预初始化页面：设置每页大小样式
function preInitHorizontalDirection () {
  // console.log(window);
  // console.log("-> " + document.getElementsByTagName('title')[0].innerText);
  const htmlElement = document.getElementsByTagName('html')[0]
  const bodyElement = document.getElementsByTagName('body')[0]

  // Required when initHorizontalDirection() is called multiple times.
  // Currently it is called only once per page.
  htmlElement.style.width = null
  bodyElement.style.width = null
  htmlElement.style.height = null
  bodyElement.style.height = null

  const bodyStyle =
    bodyElement.currentStyle || window.getComputedStyle(bodyElement)
  const paddingTop = parseInt(bodyStyle.paddingTop, 10)
  const paddingRight = parseInt(bodyStyle.paddingRight, 10)
  const paddingBottom = parseInt(bodyStyle.paddingBottom, 10)
  const paddingLeft = parseInt(bodyStyle.paddingLeft, 10)
  // console.log("-> padding = " + paddingTop + ", " + paddingRight + ", " + paddingBottom + ", " + paddingLeft);

  // document.documentElement.clientWidth is window.innerWidth excluding x scrollbar width
  const pageWidth =
    document.documentElement.clientWidth - (paddingLeft + paddingRight)
  // document.documentElement.clientHeight is window.innerHeight excluding y scrollbar height
  const pageHeight =
    document.documentElement.clientHeight - (paddingTop + paddingBottom)

  bodyElement.style.webkitColumnGap = `${paddingLeft + paddingRight}px`
  bodyElement.style.webkitColumnWidth = `${pageWidth}px`
  bodyElement.style.columnFill = 'auto'

  htmlElement.style.height = `${pageHeight + (paddingTop + paddingBottom)}px`
  bodyElement.style.height = `${pageHeight}px`

  var images = document.querySelectorAll('img')
  for (var i = 0; i < images.length; i++) {
    images[i].style.maxWidth = pageWidth > 400 ? "400px" : pageWidth + 'px'
    images[i].style.maxHeight = pageHeight > 700 ? "700px" : pageHeight + 'px'
  }

  $("rt").removeClass()
}
// 获取当前页数&初始化微调页面滚动距离
function postInitHorizontalDirection () {
  const htmlElement = document.getElementsByTagName('html')[0]
  const bodyElement = document.getElementsByTagName('body')[0]
  const bodyStyle =
    bodyElement.currentStyle || window.getComputedStyle(bodyElement)
  // var paddingTop = parseInt(bodyStyle.paddingTop, 10)
  const paddingRight = parseInt(bodyStyle.paddingRight, 10)
  // var paddingBottom = parseInt(bodyStyle.paddingBottom, 10)
  const paddingLeft = parseInt(bodyStyle.paddingLeft, 10)
  const scrollingElement = bodyOrHtml()
  // var clientWidth = document.documentElement.clientWidth
  const clientWidth = scrollingElement.clientWidth

  // var scrollWidth = document.documentElement.scrollWidth
  let scrollWidth = scrollingElement.scrollWidth
  // console.log("-> document.documentElement.offsetWidth = " + document.documentElement.offsetWidth);
  if (
    scrollWidth > clientWidth &&
    scrollWidth > document.documentElement.offsetWidth
  ) {
    scrollWidth += paddingRight
  }
  const newBodyWidth = scrollWidth - (paddingLeft + paddingRight)
  window.scrollWidth = scrollWidth

  // pageCount deliberately rounded instead of ceiling to avoid any unexpected error
  const pageCount = Math.round(scrollWidth / clientWidth)
  const pageCountFloat = scrollWidth / clientWidth

  // 单页设置width后页面宽度>屏幕宽度，导致横向滚动
  if (pageCount > 1) {
    htmlElement.style.width = `${scrollWidth}px`
    bodyElement.style.width = `${newBodyWidth}px`
  }

  if (pageCount != pageCountFloat) {
    console.warn(
      `-> pageCount = ${pageCount}, pageCountFloat = ${pageCountFloat}, Something wrong in pageCount calculation`
    )
  }

  return pageCount
}
// 自动缩放封面图片
function autoZoomCover () {
  const body = document.body
  const imgs = body.querySelectorAll('svg, img')
  const bodyStyle = body.currentStyle || window.getComputedStyle(body)
  const maxWidth = parseInt(bodyStyle.columnWidth, 10)
  const maxHeight = body.clientHeight
  if (imgs.length == 1) {
    const cover = imgs[0]
    if (cover.nodeName == 'svg') zoomSvg(cover)
    else if (cover.nodeName == 'img') zoomImg(cover)
  }

  function zoomSvg (svg) {
    svg.setAttribute('viewBox', `0 0 ${maxWidth} ${maxHeight}`)
    const image = svg.querySelector('image')
    image && image.setAttribute('width', '100%')
    image && image.setAttribute('height', '100%')
  }

  function zoomImg (img) {
    img.style.setProperty('max-width', `${maxWidth}px`, 'important')
    img.style.setProperty('max-height', `${maxHeight}px`, 'important')
  }
}
/* * *  初始化页面 end * * */

function MOJiArticleDetailSetAnalysisParagraphButton (visible) {
  if (visible) {
    document.body.classList.remove('no-analysis-paragraph-button')
  } else {
    document.body.classList.add('no-analysis-paragraph-button')
  }
}

// TODO -> Check if this is required?
function bodyOrHtml () {
  if ('scrollingElement' in document) {
    return document.scrollingElement
  }
  // Fallback for legacy browsers
  if (navigator.userAgent.includes('WebKit')) {
    return document.body
  }
  return document.documentElement
}
/**
 * @description: config
 * @return { null }
 * @param { String } theme
 */
function MOJiArticleDetailDidSetThemeNew (analysisBgColor, analysisColor, contentColor) {
  document.body.style.setProperty('--reader-analysis-background', analysisBgColor)
  document.body.style.setProperty('--analysis-color', analysisColor)
  document.body.style.setProperty('--content-color', contentColor)
}
//兼容旧的 阅读器版主题
function MOJiArticleDetailDidSetTheme (theme) {
  document.body.setAttribute('_t', theme)
}

/**
 * @description: config
 * @return { null }
 * @param { String | Number } size
 */
function MOJiArticleDetailSetFontSize (size = 1, isReload = true) {
  document.body.style.setProperty('--font-size', size)
  isReload && onMounted()
}

function onMounted () {
  initHorizontalDirection()
  scrollToCfi(currentCFI)
}

function scrollToPageIndex (pageIndex) {
  currentPageIndex = pageIndex
  req(Api.setCurrentInnerPage, pageIndex)
}

function getCurrentDOMRect () {
  const scrollElement = bodyOrHtml()
  const w = scrollElement.clientWidth
  const h = scrollElement.clientHeight
  const x = 0
  const y = 0
  return new DOMRect(x, y, w, h)
}

//ios字体样式设置
function setFontFamily (fontFamily, isReload = true) {
  document.body.style.setProperty('--custom-font-family', fontFamily)
  isReload && onMounted()
}
/**
 * Gets the first partially or completely visible node in viewportRect
 * @param {Node} node Accepts {@link Element} or {@link Text}
 * @returns {(Node|null)} Returns {@link Element} or {@link Text} or null
 */
function getFirstVisibleNode (node) {
  const range = document.createRange()
  range.selectNode(node)
  const rect = RangeFix.getBoundingClientRect(range)
  if (rect == null) return null

  const intersects = rectIntersects(viewportRect, rect)
  const contains = rectContains(viewportRect, rect)

  const isBr = node.tagName && node.tagName.toLowerCase() === "br"
  const isReaderAnalysis = node.classList && node.classList.contains("reader-analysis")

  if (contains && !isBr && !isReaderAnalysis) {
    // node's rect is completely inside viewportRect.
    return node
  } else if (intersects) {
    const childNodes = node.childNodes
    for (let i = 0; i < childNodes.length; i++) {
      // EPUB CFI ignores nodes other than ELEMENT_NODE and TEXT_NODE
      // http://www.idpf.org/epub/linking/cfi/epub-cfi.html#sec-path-child-ref

      if (
        childNodes[i].nodeType === Node.ELEMENT_NODE ||
        childNodes[i].nodeType === Node.TEXT_NODE
      ) {
        const childNode = getFirstVisibleNode(childNodes[i])
        if (childNode) {
          return childNode
        }
      }
    }

    // No children found or no child's rect completely inside viewportRect,
    // so returning this node as it's rect intersected with viewportRect.
    intersectNode = node
  }
  return null
}

/**
 * @description: 翻到元素所在页面
 * @return { Number } ios only
 * @param { String } cfi
 */
function scrollToCfi (cfi) {
  if (!cfi) return

  try {
    const $node = EPUBcfi.Interpreter.getTargetElement(cfi, document)
    scrollToNodeOrRange($node[0])
  } catch (e) {
    console.error(`scrollToCfi -> ${e}`)
  }
}

function scrollToNodeOrRange (nodeOrRange) {
  const pageIndex = getPageIndexByNodeOrRange(nodeOrRange)
  // var newScrollLeft = clientWidth * pageIndex
  // console.log("-> newScrollLeft = " + newScrollLeft);
  // scrollingElement.scrollLeft = newScrollLeft
  scrollToPageIndex(pageIndex)
  // break
  // }

  // return nodeOrRange
  // return pageIndex
}

function getPageIndexByNodeOrRange (nodeOrRange) {
  const scrollingElement = bodyOrHtml()
  // var direction = FolioWebView.getDirection()

  // For Direction.VERTICAL
  // var nodeOffsetTop, nodeOffsetHeight

  // For Direction.HORIZONTAL
  let nodeOffsetLeft

  if (nodeOrRange instanceof Range || nodeOrRange.nodeType === Node.TEXT_NODE) {
    let rect
    if (nodeOrRange.nodeType && nodeOrRange.nodeType === Node.TEXT_NODE) {
      const range = document.createRange()
      range.selectNode(nodeOrRange)
      rect = RangeFix.getBoundingClientRect(range)
    } else {
      rect = RangeFix.getBoundingClientRect(nodeOrRange)
    }
    // nodeOffsetTop = scrollingElement.scrollTop + rect.top
    // nodeOffsetHeight = rect.height
    nodeOffsetLeft = scrollingElement.scrollLeft + rect.left
  } else if (nodeOrRange.nodeType === Node.ELEMENT_NODE) {
    // nodeOffsetTop = nodeOrRange.offsetTop
    // nodeOffsetHeight = nodeOrRange.offsetHeight
    nodeOffsetLeft = nodeOrRange.offsetLeft
  } else {
    throw `-> Illegal Argument Exception, nodeOrRange -> ${nodeOrRange}`
  }

  // TODO 暂时先做水平翻页
  // switch (direction) {

  // case Direction.VERTICAL:
  //     var topDistraction = FolioWebView.getTopDistraction(DisplayUnit.DP)
  //     var pageTop = scrollingElement.scrollTop + topDistraction
  //     var pageBottom = scrollingElement.scrollTop + document.documentElement.clientHeight
  //         - FolioWebView.getBottomDistraction(DisplayUnit.DP)

  //     var elementTop = nodeOffsetTop - 20
  //     elementTop = elementTop < 0 ? 0 : elementTop
  //     var elementBottom = nodeOffsetTop + nodeOffsetHeight + 20
  //     var needToScroll = (elementTop < pageTop || elementBottom > pageBottom)

  //     //console.log("-> topDistraction = " + topDistraction);
  //     //console.log("-> pageTop = " + pageTop);
  //     //console.log("-> elementTop = " + elementTop);
  //     //console.log("-> pageBottom = " + pageBottom);
  //     //console.log("-> elementBottom = " + elementBottom);

  //     if (needToScroll) {
  //         var newScrollTop = elementTop - topDistraction
  //         newScrollTop = newScrollTop < 0 ? 0 : newScrollTop
  //         //console.log("-> Scrolled to = " + newScrollTop);
  //         scrollingElement.scrollTop = newScrollTop
  //     }
  //     break

  // case Direction.HORIZONTAL:
  const clientWidth = scrollingElement.clientWidth // document.documentElement.clientWidth
  const pageIndex = Math.floor(nodeOffsetLeft / clientWidth)
  return pageIndex
}

/**
 * Returns true iff the two specified rectangles intersect. In no event are
 * either of the rectangles modified.
 *
 * @param {DOMRect} a The first rectangle being tested for intersection
 * @param {DOMRect} b The second rectangle being tested for intersection
 * @returns {boolean} returns true iff the two specified rectangles intersect.
 */
function rectIntersects (a, b) {
  return (
    a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
  )
}

/**
 * Returns true iff the specified rectangle b is inside or equal to
 * rectangle b. An empty rectangle never contains another rectangle.
 *
 * @param {DOMRect} a The rectangle being tested whether rectangle b is inside this or not.
 * @param {DOMRect} b The rectangle being tested for containment.
 * @returns {boolean} returns true iff the specified rectangle r is inside or equal to this rectangle
 */
function rectContains (a, b) {
  // check for empty first
  return (
    a.left < a.right &&
    a.top < a.bottom &&
    // now check for containment
    a.left <= b.left &&
    a.top <= b.top &&
    a.right >= b.right &&
    a.bottom >= b.bottom
  )
}

function setBodyOpacity (value = 1) {
  document.body.style.opacity = value
}

/**
 * @description: 发送请求至app
 * @return {*}
 * @param { string } method which defined in app
 * @param { object | String } params ios只能传String
 */
function req (method, params = '') {
  try {
    if (isIOS) {
      window.webkit.messageHandlers[method].postMessage(params)
    } else {
      ReaderJsInterface[method](params)
    }
  } catch (error) {
    // console.error(error)
  }
}

/* * * 增加点击事件 * * */
$(() => {
  window.ssReader = Class({
    $singleton: true,

    init () {
      rangy.init()
    },
    base64encode (str) {
      return btoa(unescape(encodeURIComponent(str)))
    },

    base64decode (str) {
      return decodeURIComponent(escape(atob(str)))
    },
    clearSelection () {
      if (window.getSelection) {
        if (window.getSelection().empty) {
          // Chrome
          window.getSelection().empty()
        } else if (window.getSelection().removeAllRanges) {
          // Firefox
          window.getSelection().removeAllRanges()
        }
      } else if (document.selection) {
        // IE?
        document.selection.empty()
      }
    },
  })

  if (typeof ssReader !== 'undefined') {
    ssReader.init()
  }

  if ($('span[a-token]').length > 0) {
    $('span[a-token], ruby').click((e) => {
      const selectedNode = e.target
      const curTagText = selectedNode.textContent

      curTagText && doSearch(selectedNode, curTagText)
    })

    function doSearch (node, text) {
      window.getSelection().removeAllRanges()
      node.classList.add('on-click-word')
      setTimeout(() => {
        node.classList.remove('on-click-word')
      }, 500)
      req(Api.clickWord, text)
    }
  } else {
    $('p[moji-id], div[moji-id]').click(function (event) {
      event.preventDefault()
      event.stopPropagation()

      if ($('span.on-click-word').length > 0) return
      if ($('span.active').length > 0) return

      if (event.target.className.includes('reader-analysis')) {
        /**
         * 点击 析 按钮
         */
        const btn = event.target
        const content =
          btn.parentNode.outerHTML.replace(/<rt.*?<\/rt>/g, '')
            .replace(/<[^>]+>/g, '')
            .trimEnd()
            .trimStart()
        console.log('outerHTML:', btn.parentNode.outerHTML, 'textContent:', btn.parentNode.textContent, 'content', content)
        btn.classList.add('active')
        setTimeout(function () { btn.classList.remove('active') }, 200)
        req(Api.analysisParagraph, content)
      } else {
        /**
         * 点击句子
         */


        let boundingRect, content, contentEl, startIndex = 0, endIndex = 0, index = 0

        console.log('event.target.tagName', event.target.tagName, event.target, event.target.innerHTML)

        contentEl = event.currentTarget
        content = contentEl.innerHTML

        const selection = window.getSelection()
        const selectNode = selection.baseNode
        if (!selectNode) return
        if (selectNode.nodeType === document.TEXT_NODE) {
          index = content.indexOf(selectNode.data) + selection.baseOffset
          console.log("selectNode", selectNode, selectNode.data, content.indexOf(selectNode.textContent))
        }

        boundingRect = selection.getRangeAt(0).getBoundingClientRect()
        console.log('boundingRect', boundingRect, selection)

        console.log('index', index)
        console.log('content[index]', content[index])

        const tagPosition = [].concat(
          getImageTagPositions(content),
          getATagPositions(content),
          getXMLTagPositions(content),
          getSpanAnalysisTagPositions(content)
        )
        console.log("tagPosition", tagPosition)

        const textContentPosition = matchTextContentPositions(contentEl)
        console.log("textContentPosition", textContentPosition)

        for (let i = index; i >= 0; i--) {
          if (/[。.？?!！．」「]/.test(content[i]) && i !== index) {
            if (
              !tagPosition.length ||
              !tagPosition.some(position => i >= position.start && i <= position.end)
            ) {
              startIndex = i + 1
              break
            }
          }
        }
        console.log('startIndex', startIndex, content[startIndex])

        for (let i = index; i < content.length; i++) {
          if (/[。.？?!！．]/.test(content[i])) {
            if (
              !tagPosition.length ||
              !tagPosition.some(position => i >= position.start && i <= position.end)
            ) {
              endIndex = i + 1
              break
            }
          }
        }
        endIndex = endIndex > 0 ? endIndex : content.length
        console.log('endIndex', endIndex, content[endIndex])

        const selectContentRanges = getIntersectionRanges([startIndex, endIndex], textContentPosition, content)
        console.log("selectContentRanges", selectContentRanges)

        const selectTextContent = content.slice(startIndex, endIndex).replace(/<rt\b[^>]*>.*?<\/rt>/g, "").replace(/<[^>]*>/g, "").replace(/&nbsp;/g, "").trim()
        console.log("selectTextContent", selectTextContent)

        if (contentEl && selectTextContent) {
          contentEl.innerHTML = wrapContentWithTags(content, selectContentRanges)

          window.MOJiArticleDetailSentenceInactive = function () {
            $('.on-click-word').contents().unwrap()
          }

          const highlightDom = document.querySelectorAll('.on-click-word');
          const firstDom = highlightDom[0].childNodes[0];
          const lastDom = highlightDom[highlightDom.length - 1].childNodes[0];

          const firstDomTextContent = firstDom.textContent;
          const firstRange = document.createRange();
          firstRange.setStart(firstDom, 0);
          firstRange.setEnd(firstDom, 1);
          const firstDomSite = firstRange.getBoundingClientRect();


          const lastDomTextContent = lastDom.textContent;
          const lastRange = document.createRange();
          lastRange.setStart(lastDom, lastDomTextContent.length - 1);
          lastRange.setEnd(lastDom, lastDomTextContent.length);
          const lastDomSite = lastRange.getBoundingClientRect();

          console.log("firstDomSite, lastDomSite", firstDomSite, lastDomSite)

          req(Api.selectSentence, JSON.stringify(
            Object.assign(
              { text: selectTextContent },
              JSON.parse(JSON.stringify(boundingRect)),
              { firstDomSite, lastDomSite },
              { windowWidth: window.innerWidth, windowHeight: window.innerHeight }
            )
          ))
        }
      }
    })
  }
})

window.addEventListener('load', () => {
  preInitHorizontalDirection()
})

document.onreadystatechange = function () {
  if (document.readyState == 'complete') {
    // 此时无平台信息，只能用ua做区分
    if (/iP(hone|od|ad)|Macintosh/.test(navigator.userAgent)) {
      window.webkit.messageHandlers.onJsLoaded.postMessage('')
    } else {
      ReaderJsInterface.onJsLoaded()
    }
    document.onreadystatechange = null
  }
}

window.onbeforeunload = function () {
  horizontalInterval && clearInterval(horizontalInterval)
}

function getImageTagPositions (text) {
  let tagPositions = []
  const regex = /<img[^>]+src\s*=\s*['"]([^'"]+)['"][^>]*>/g
  let match
  while ((match = regex.exec(text)) !== null) {
    tagPositions.push({
      start: match.index,
      end: match.index + match[0].length - 1
    })
  }
  return tagPositions
}

function getATagPositions (text) {
  let tagPositions = []
  const regex = /<a\s+.*?href="(.*?)".*?>(.*?)<\/a>/g
  let match
  while ((match = regex.exec(text)) !== null) {
    tagPositions.push({
      start: match.index,
      end: match.index + match[0].length - 1
    })
  }
  return tagPositions
}

function getXMLTagPositions (text) {
  let tagPositions = []
  const regex = /<[^>]*xmlns[^>]*>/g
  let match
  while ((match = regex.exec(text)) !== null) {
    tagPositions.push({
      start: match.index,
      end: match.index + match[0].length - 1
    })
  }
  return tagPositions
}

function getSpanAnalysisTagPositions (text) {
  let tagPositions = []
  const regex = /<span class="reader-analysis">[\s\S]*?<\/span>/g
  let match
  while ((match = regex.exec(text)) !== null) {
    tagPositions.push({
      start: match.index,
      end: match.index + match[0].length - 1
    })
  }
  return tagPositions
}

function matchTextContentPositions (contentEl) {
  const content = contentEl.innerHTML
  const positions = []
  const tagPositions = getAllTagPositions(content)

  const findIndex = (content, text, start) => {
    const index = content.indexOf(text, start)
    if (tagPositions.findIndex(pos => index >= pos.start && index <= pos.end) >= 0) {
      return findIndex(content, text, index + 1)
    } else {
      return index
    }
  }

  const traverseNodes = (node, startIdx) => {
    if (node.nodeType === Node.TEXT_NODE && node.textContent.trim() !== "") {
      const text = node.textContent.trimStart()
      const idx = findIndex(content, text, startIdx)

      if (idx !== -1) {
        const start = idx
        const end = start + text.length
        positions.push([start, end, content.slice(start, end)])
        startIdx = end
      }
    }

    for (let childNode of node.childNodes) {
      if (!childNode.tagName || childNode.tagName.toUpperCase() !== "RT") {
        startIdx = traverseNodes(childNode, startIdx)
      }
    }

    return startIdx
  }

  traverseNodes(contentEl, 0)

  return positions
}

function getIntersectionRanges (range, intervals, content) {
  let result = []
  for (let i = 0; i < intervals.length; i++) {
    let interval = intervals[i]
    let start = Math.max(range[0], interval[0])
    let end = Math.min(range[1], interval[1])

    if (start <= end) {
      result.push([start, end, content.slice(start, end)])
    }
  }
  return result.filter(pos => pos[0] !== pos[1])
}

function wrapContentWithTags (content, ranges) {
  let result = ''
  let currentIndex = 0
  ranges.forEach(([start, end]) => {
    result += content.substring(currentIndex, start)
    result += `<span class="on-click-word">`
    result += content.substring(start, end)
    result += `</span>`
    currentIndex = end
  })

  result += content.substring(currentIndex)
  return result
}

function getAllTagPositions (text) {
  const tagRegex = /<[^>]+>/g
  const tagMatches = [...text.matchAll(tagRegex)]

  const tagPositions = tagMatches.map(match => {
    const start = match.index
    const end = match.index + match[0].length - 1
    return {
      start,
      end,
      content: text.slice(start, end + 1)
    }
  })

  return tagPositions
}
