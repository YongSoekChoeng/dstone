/**
 * AI 화면의 왼쪽 메뉴 목록입니다. 그리는 것은 dstone-common 의 console.js(DstoneConsole.sidebar)가 합니다.
 * 메뉴 한 줄: [화면 주소, 이름, 아이콘 이름]. 지금 화면의 주소와 같은 줄을 강조합니다.
 */
document.addEventListener("DOMContentLoaded", function () {
	DstoneConsole.sidebar({
		brand: "AI",
		mark: "AI",
		home: "/ai/index.html",
		group: "AI",
		menus: [
			["/ai/index.html", "개요", "home"],
			["/ai/chat/chat.html", "채팅", "chat"],
			["/ai/workflow/workflow.html", "Workflow 테스트", "play"],
			["/ai/admin/workflow/workflow.html", "Workflow 실행 관리", "list"],
			["/ai/designer/designer.html", "Workflow 디자이너", "flow"]
		]
	});
});
