# vs code markdown 快捷键

{
    /*
    // Place your snippets for Markdown here. Each snippet is defined under a snippet name and has a prefix, body and 
    // description. The prefix is what is used to trigger the snippet and the body will be expanded and inserted. Possible variables are:
    // $1, $2 for tab stops, $0 for the final cursor position, and ${1:label}, ${2:another} for placeholders. Placeholders with the 
    // same ids are connected.
    // Example:
    "Print to console": {
        "prefix": "log",
        "body": [
            "console.log('$1');",
            "$2"
        ],
        "description": "Log output to console"
    }
*/
    "Insert heading1": {
        "prefix": "heading1",
        "body": "# ${1:text}",
        "description": "Insert heading1"
    },
    "Insert heading2": {
        "prefix": "heading2",
        "body": "## ${1:text}",
        "description": "Insert heading2"
    },
    "Insert heading3": {
        "prefix": "heading3",
        "body": "### ${1:text}",
        "description": "Insert heading3"
    },
    "Insert heading4": {
        "prefix": "heading4",
        "body": "#### ${1:text}",
        "description": "Insert heading4"
    },
    "Insert heading5": {
        "prefix": "heading5",
        "body": "##### ${1:text}",
        "description": "Insert heading5"
    },
    "Insert heading6": {
        "prefix": "heading6",
        "body": "####### ${1:text}",
        "description": "Insert heading6"
    },
    "Insert table": {
        "prefix": "table",
        "body": "Column A | Column B | Column C\n---------|----------|---------\nA1 | B1 | C1\nA1 | B1 | C1\nA1 | B1 | C1",
        "description": "Insert table"
    },
    "Insert strikethrough": {
        "prefix": "strikethrough",
        "body": "~~$0~~",
        "description": "Insert strikethrough"
    },
    "Insert mermaid": {
        "prefix": "mermaid",
        "body": "```mermaid\ngraph LR\nA-->B\n```",
        "description": "Insert mermaid"
    },
    "Insert flow": {
        "prefix": "flow",
        "body": "```flow\nst=>start: Start\nop=>operation: Your Operation\ncond=>condition: Yes or No?\ne=>end\nst->op->cond\ncond(yes)->e\ncond(no)->op\n```",
        "description": "Insert flow"
    },
    "Insert sequence": {
        "prefix": "sequence",
        "body": "```sequence\nA->>B: How are you?\nB->>A: Great!\n```",
        "description": "Insert sequence"
    },
    "Insert completed": {
        "prefix": "completed",
        "body": "- [x] ",
        "description": "Insert completed"
    },
    "Insert imcomplete": {
        "prefix": "imcomplete",
        "body": "- [ ] ",
        "description": "Insert imcomplete"
    },
    "Insert insert": {
        "prefix": "insert",
        "body": "++$0++",
        "description": "Insert insert"
    },
    "Insert sepcifiedcode": {
        "prefix": "sepcifiedcode",
        "body": "```${1:type}\n\n```",
        "description": "Insert sepcifiedcode"
    },
    "Insert math": {
        "prefix": "math",
        "body": "```math\n$0\n```",
        "description": "Insert math"
    }
}