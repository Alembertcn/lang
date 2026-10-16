var setting = {
	"settings": [
		{
			"type": "pageConfig",
			"data": {
				"bottomSpace": 55, //传一个单位为px的数值来预留底部空隙
				"platform": "ios",
				"font": "MOJiContentFontDefault"
				"voiceIcon": "standard / memoryCard"
			}
		},
		{
			"type": "displayContent",
			"data": {
				"pageType": "word",
				"word": {
					"title": "",								//词语原文
					"partsOfSpeech": ["", ""],				//这个要native在传参的时候便利生成一下减少js部分的逻辑
					"knowledgeID": "",
					"pron": "",								//读音
					"tags": "",								//关联辞书，用分隔符#分隔
					"accent": "",							//读音
					"detailsList": [							//对应多个翻译
						{
							"detailsID": "",
							"knowledgeID": "",
							"title": "",
							"subdetails": [
								{
									"subdetailsID": "",
									"detailsID": "",
									"knowledgeID": "",
									"title": "",
									"examples": [
										{
											"exampleID": "",
											"subdetailsID": "",
											"knowledgeID": "",
											"title": "",
											"translation": ""
										}
									]
								}
							]
						}
					]
				}
			}
		},
		{
			"type": "displayContent",
			"data": {
				"pageType": "sentence",
				"sentence": {
					"title": "",								//例句原文
					"translation": "",
					"objectID": "",								//例句id
					"author": {									//谁创建
						"portrait": "",
						"title": "",//名字
						"sign": "",//签名
						"id": ""
					}								
				}
			}
		},
		{
		    "type": "displayContent",
            "data": {
                "pageType": "voiceSentence",
                "voiceSentence": {
                    "title": "",								//例句原文
                    "translation": "",
                    "objectID": "",								//例句id
                    "author": {									//谁创建
                        "portrait": "",
                        "title": "",//名字
                        "sign": "",//签名
                        "id": ""
                    }
                }
            }
		}
		{
			"type": "displayContent",
			"data": {
				"pageType": "example",
				"example": {
					"title": "",								//例句原文
					"translation": "",
					"objectID": "",								//自创例句id
					"excerpt": { //关联的词条
						"title": "", //标题
						"id": "",
						"subTitles": "",//子标题
						"tags":""
					}
				}
			}
		},
		{
			"type": "updateFurigana",
			"data": {
				"furiganaType": "hira",
				"maping": {									//当前选择的注音类型为(hira/romaji/none)，注意仅返回这种类型的注音内容
					"knowledgeID|detailsID|subdetailsID|exampleID": [		//联合id作为key，id为空则用-1作为占位符，分隔符|
						{
							"org": "",
							"hira": "",
							"romaji": ""
						}
					],
					"knowledgeID|detailsID|subdetailsID|exampleID": [
						{
							"org": "",
							"hira": "",
							"romaji": ""
						}
					]
				}
			}
		},
		{
			"type": "updateTheme",
			"data": {
				"css": "color1"
			}
		},
		// 取消foldContent，使用autoFoldWordExplanation
		{
			"type": "foldContent"
		},
		// 点击右上角、初始化词条收展状态，都调用这个方法
		{
			"type": "autoFoldWordExplanation",
			"data": {
				"shouldFold": "true|false" // 收起｜展开
			}
		},
		{
			"type": "updateReadingStatus",
			"data": {
				"ids":'on|off|load|infinite' // 播放｜暂停｜加载｜无限循环
			}
		},
		{
			"type": "setFavourite",
			"data": {
				"ids":'true|false'
			}
		}
	]
}

var interactionToNative = [
	{
		"type": "callWordAlert", //词条弹窗
        "value": "猫"
    },
    {
    	"type": "read", //阅读文本
    	"value": "text need to be read on native"
    },
    {
    	"type": "readObject", //阅读对象
    	"data": {
    		"objectType": "word", //word/example/sentence
	    	"knowledgeID": "",
	    	"exampleID": "",
	    	"subdetailsID": "",
			"detailsID":"",
	    	"text": "text need to be read on native"
    	}
    },
    {
    	"type": "longPressObject", //纠正发音
    	"data": {
    		"objectType": "word", //word/example/sentence
	    	// "wordID": "",
	    	// "exampleID": "",
	    	// "sentenceID": "",
			"knowledgeID": "", // knowledgeID ｜ exampleID ｜ objectID
			"text": ""
    	}
    },
    {
    	"type": "tapTag", //点击标签（如单词的词性），暂时无交互，预留
    	"data": {
    		"tag": "",
	    	"tagID": ""
    	}
    },
    {
    	"type": "setFavourite",  //传递收藏状态
    	"data": {
    		"knowledgeID|detailsID|subdetailsID|exampleID": True,
    		"knowledgeID|detailsID|subdetailsID|exampleID": False,
    		"knowledgeID|detailsID|subdetailsID|exampleID": True,
    		"knowledgeID|detailsID|subdetailsID|exampleID": True
    	}
    },
	{
		"type" : "setWordExampleFavourite",
		"data": {
            "exampleID": "",
            "subdetailsID": "",
            "knowledgeID": "",
            "detailsID": "",
            "status": "true|false"
        }
	},
	// 跳转到个人中心
	{
		"type": "showPersonal",
		"data": {
			"userId": ""
		}
	},
	// 跳转到单词页面
	{
		"type": "showWordPage",
        "data": {
            "wordID": ""
        }
	},
	// 点击翻页
	{
		"type": "switchPageManually",
        "data": {
            "direction":  "forward|reverse" // 下一页|上一页
        }
	},
	// 点击单词解释，传递当前收展状态
	{
		"type": "clickToFoldWordExplanation",
        "data": {
            "foldState": "1|0", // true｜false (单个收起｜单个展开)
			"allFoldState": "1|2|3"  // 1 全部收起 2全部展开 3部分展开
        }
	}, 
	// 点击动词活用，展示原来的动词活用弹窗
	{
		"type": "seeMoreForms"
	}
]

// JS方法目录
var toolsMethods = [
	"updateStatus",
	"scrollToTarget",
	"rgb",
	"colorRGBtoHex",
	"getTagColorByPrefix",
	"createUuid",
	"fixErrorFuriganaByWords",

	// document
	"ElementCreator",
	"addHeaderView",
	"createCardView",
	"createIconEffect",
	"createLangToolkit",
	"appendCSSById"
]

var nativeEventMethods = [
	"triggerActionInNative", // 请求入口

	"nativeRead", // 已弃用
	"nativeReadObject",
	"nativeSetWordExampleFavourite",
	"nativeCallWordAlert",
	"nativeLongPressObject",
	"nativeShowPersonal",
	"nativeShowWordPage",
	"nativeSwitchPageManually",
	"nativeClickToFold"
]






