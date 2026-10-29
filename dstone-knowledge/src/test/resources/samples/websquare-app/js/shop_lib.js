/**
 * 화면 공통 함수 (HI-Japan 의 js/wq/com_lib.js 를 줄인 것)
 * 예: shopLib.openPopup("ORDER", "/ui/common/Header.xml", options); 처럼 주석 안의 경로는 보지 않는다.
 */
var shopLib = {};

/* 팝업을 연다. 경로를 변수로 받으므로 이 함수만으로는 어느 화면인지 모른다. */
shopLib.openPopup = function(id, url, options) {
	var re = /["{]/g;
	WebSquare.util.openPopup("/shop" + url, options);
};

/* 주문 상세 팝업. 여는 화면이 본문에 적혀 있다. 지워 둔 옛 화면은 주석 안이다. */
shopLib.openOrderPopup = function(param) {
	var options = { title : "주문 상세 {팝업}", popupParam : param };
//	shopLib.openPopup("OLD", "/ui/common/Header.xml", options);
	shopLib.openPopup("ORDER", "/ui/order/OrderPopup.xml", options);
};

/* 결과가 하나면 바로 돌려주고, 아니면 다른 공통 함수로 팝업을 연다. */
shopLib.searchOrder = function(param) {
	ShopJS.svr.doRequestAjax("ORD0100M01S", {
		callback : {
			success : function(res) {
				if (res.length != 1) {
					shopLib.openOrderPopup(param);
				}
			}
		}
	});
};

/* 주소로 서버를 부른다. 이 함수를 부르는 화면이 그 주소의 진입점과 이어진다. */
shopLib.printOrder = function(orderNo) {
	$p.ajax({ url : "/order/detail.do", data : { orderNo : orderNo } });
};

/* 같은 이름이 다른 파일에도 없으므로 쓰인다. 화면을 열지 않는 함수는 목록에 남지 않는다. */
function formatAmount(value) {
	return value / 100;
}
