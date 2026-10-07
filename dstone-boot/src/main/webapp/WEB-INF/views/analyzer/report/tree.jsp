<%@page import="net.dstone.common.utils.SystemUtil"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%    

/******************************************* 변수 선언 시작 *******************************************/              
net.dstone.common.utils.RequestUtil requestUtil = new net.dstone.common.utils.RequestUtil(request, response);

/******************************************* 변수 선언 끝 *********************************************/           
    

%> 
<!DOCTYPE HTML>

<html>

	<!-- Head -->
	<jsp:include page="../common/head.jsp"></jsp:include> 
	
	<!-- Script -->
	<script type="text/javascript"> 
	
		$(document).ready(function(){
			init();
		});

	</script>


	<script id="code">
		var sysId = "";
		var maxLevel = 0;
		var names = {}; // hash to keep track of what names have been used

		var treeData = new Array();
		treeData[treeData.length] = {"key":"ROOT","name":sysId};
	
		function init() {
			
			sysId = opener.document.getElementById("SYS_ID");
			maxLevel = opener.maxLevel;
			
			// Since 2.2 you can also author concise templates with method chaining instead of GraphObject.make
			// For details, see https://gojs.net/latest/intro/buildingObjects.html
			const $ = go.GraphObject.make; // for conciseness in defining templates
	
			myDiagram = new go.Diagram('myDiagramDiv', {
				initialAutoScale : go.AutoScale.UniformToFill,
				// define the layout for the diagram
				layout : $(go.TreeLayout, {
					nodeSpacing : 5,
					layerSpacing : (maxLevel*10),
					arrangement : go.TreeArrangement.FixedRoots
				}),
			});
	
			// Define a simple node template consisting of text followed by an expand/collapse button
			myDiagram.nodeTemplate = $(go.Node, 'Horizontal', {
				selectionChanged : nodeSelectionChanged
			}, // this event handler is defined below
			$(go.Panel, 'Auto', $(go.Shape, {
				fill : '#1F4963',
				stroke : null
			}), $(go.TextBlock, {
				font : 'bold 13px Helvetica, bold Arial, sans-serif',
				stroke : 'white',
				margin : 3,
			}, new go.Binding('text', 'key'))), $('TreeExpanderButton'));
	
			// Define a trivial link template with no arrowhead.
			myDiagram.linkTemplate = $(go.Link, {
				selectable : false
			}, $(go.Shape)); // the link shape
	
			// create the model for the DOM tree
			myDiagram.model = new go.TreeModel({
				isReadOnly : true, // don't allow the user to delete or copy nodes
				// build up the tree in an Array of node data
				//nodeDataArray: traverseDom(document.activeElement),
				nodeDataArray : transTreeData()
			//nodeDataArray: testData
			});
		}
	
		function transTreeData() {
			var tData = new Array();
			var row = new Object();
			
			tData[tData.length] = {"key":"ROOT","name":"anybiz" };
			
			if( opener.overAllData ){
				var data = opener.overAllData;
				var dataRow = new Object();
				var isAlreadyAdded = false;
				for(var i=0; i<data.length; i++){
					dataRow = data[i];
	 				if( dataRow["BASIC_URL"] != ""){
	 					
	 					// 1. UI
		 				isAlreadyAdded = false;
		 				for(var k=0; k<tData.length; k++){
		 					if(tData[k]["key"] == dataRow["UI_ID"]){
		 						isAlreadyAdded = true;
		 						break;
		 					}
		 				}
		 				if( !isAlreadyAdded ){
							row = new Object();
							if(dataRow["UI_ID"]){
								row["key"] 			= dataRow["UI_ID"];
							}else{
								row["key"] 			= "";
							}
			 				row["name"] 		= dataRow["UI_NM"];
			 				row["parent"] 		= "ROOT";
		 					tData[tData.length] = row;
console.log("UI================>>>" + JSON.stringify( row ) );		 					
		 				}

						// 2. URL
						if( dataRow["BASIC_URL"] ){
			 				isAlreadyAdded = false;
			 				for(var k=0; k<tData.length; k++){
			 					if(tData[k]["key"] == dataRow["BASIC_URL"]){
			 						isAlreadyAdded = true;
			 						break;
			 					}
			 				}
			 				if( !isAlreadyAdded ){
								row = new Object();
				 				row["key"] 			= dataRow["BASIC_URL"];
				 				row["name"] 		= "";
				 				if(dataRow["UI_ID"]){
				 					row["parent"] 		= dataRow["UI_ID"];
				 				}else{
				 					row["parent"] 		= "ROOT";
				 				}
				 				
			 					tData[tData.length] = row;
console.log("URL================>>>" + JSON.stringify( row ) );		
			 				}
						}

						// 3. API
						var lastApiIndex = 0;
						for(var n=0; n<maxLevel; n++){
							lastApiIndex = n;
							if( !dataRow["FUNCTION_ID_"+(n+1)]){
								break;
							}
							if( dataRow["FUNCTION_ID_"+(n+1)] == ""){
								break;
							}
							row = new Object();
			 				row["key"] 			= dataRow["FUNCTION_ID_"+(n+1)];
			 				row["name"] 		= dataRow["FUNCTION_NAME_"+(n+1)];
			 				if(n == 0 ){
			 					row["parent"] 		= dataRow["BASIC_URL"];
			 				}else{
			 					row["parent"] 		= dataRow["FUNCTION_ID_"+(n)];
			 				}
		 					tData[tData.length] = row;
console.log("API================>>>" + JSON.stringify( row ) );		
						}

						// 4. TABLE
						if( dataRow["CALL_TBL"] == ""){
							if( !dataRow["CALL_TBL"] ){
								break;
							}
							if( dataRow["CALL_TBL"] == ""){
								break;
							}
							row = new Object();
			 				row["key"] 			= dataRow["CALL_TBL"];
			 				row["name"] 		= "";
			 				row["parent"] 		= dataRow["FUNCTION_ID_"+(lastApiIndex+1)];
		 					tData[tData.length] = row;
console.log("TABLE================>>>" + JSON.stringify( row ) );		
						}
	 				}
				}
			}
			
			/*
			row = {"key":"A","name":"노드A","parent":"ROOT"}; tData[tData.length] = row;
			row = {"key":"A-1","name":"노드A-1","parent":"A"}; tData[tData.length] = row;
			row = {"key":"A-1-1","name":"노드A-1-1","parent":"A-1"}; tData[tData.length] = row;
			row = {"key":"A-1-1-1","name":"노드A-1-1-1","parent":"A-1-1"}; tData[tData.length] = row;
			row = {"key":"A-1-1-2","name":"노드A-1-1-2","parent":"A-1-1"}; tData[tData.length] = row;
			row = {"key":"A-1-1-3","name":"노드A-1-1-3","parent":"A-1-1"}; tData[tData.length] = row;
			row = {"key":"A-1-2","name":"노드A-1-2","parent":"A-1"}; tData[tData.length] = row;
			row = {"key":"A-1-2-1","name":"노드A-1-2-1","parent":"A-1-2"}; tData[tData.length] = row;
			row = {"key":"A-1-2-2","name":"노드A-1-2-2","parent":"A-1-2"}; tData[tData.length] = row;
			row = {"key":"A-1-2-3","name":"노드A-1-2-3","parent":"A-1-2"}; tData[tData.length] = row;

			row = {"key":"A-2","name":"노드A-2","parent":"A"}; tData[tData.length] = row;
			row = {"key":"A-2-1","name":"노드A-2-1","parent":"A-2"}; tData[tData.length] = row;
			row = {"key":"A-2-1-1","name":"노드A-2-1-1","parent":"A-2-1"}; tData[tData.length] = row;
			row = {"key":"A-2-1-2","name":"노드A-2-1-2","parent":"A-2-1"}; tData[tData.length] = row;
			row = {"key":"A-2-1-3","name":"노드A-2-1-3","parent":"A-2-1"}; tData[tData.length] = row;
			
			row = {"key":"B","name":"노드B","parent":"ROOT"}; tData[tData.length] = row;
			row = {"key":"B-1","name":"노드B-1","parent":"B"}; tData[tData.length] = row;
			row = {"key":"B-1-1","name":"노드B-1-1","parent":"B-1"}; tData[tData.length] = row;
			row = {"key":"B-1-1-1","name":"노드B-1-1-1","parent":"B-1-1"}; tData[tData.length] = row;
			row = {"key":"B-1-1-2","name":"노드B-1-1-2","parent":"B-1-1"}; tData[tData.length] = row;
			row = {"key":"B-1-1-3","name":"노드B-1-1-3","parent":"B-1-1"}; tData[tData.length] = row;
			row = {"key":"A-1-1-1","name":"노드A-1-1-1","parent":"B"}; tData[tData.length] = row;
			*/
			
			/*
			row = {"key":"kr.co.gnx.performance.performance.PerformanceService.getProductGroupMonthAchievementsList(kr.co.gnx.performance.performance.PerformanceVO)","name":"보종별마감업적리스트조회","parent":"kr.co.gnx.performance.performance.PerformanceController.getProductGroupMonthAchievementsList(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, kr.co.gnx.performance.performance.PerformanceVO)"}; tData[tData.length] = row;
			row = {"key":"kr.co.gnx.contract.productgroup.ProductGroupDAO.selectProductgroupList(kr.co.gnx.contract.productgroup.ProductGroupVO)","name":"상품군관리목록조회","parent":"kr.co.gnx.performance.performance.PerformanceService.getProductGroupMonthAchievementsList(kr.co.gnx.performance.performance.PerformanceVO)"}; tData[tData.length] = row;
			row = {"key":"kr.co.gnx.performance.performance.PerformanceController.getProductGroupMonthAchievementsList(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, kr.co.gnx.performance.performance.PerformanceVO)","name":"보종별마감업적조회","parent":"/performance/monthAchievements/getProductGroupMonthAchievementsList.ajax"}; tData[tData.length] = row;
			row = {"key":"kr.co.gnx.performance.performance.PerformanceService.getProductGroupMonthAchievementsList(kr.co.gnx.performance.performance.PerformanceVO)","name":"보종별마감업적리스트조회","parent":"kr.co.gnx.performance.performance.PerformanceController.getProductGroupMonthAchievementsList(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, kr.co.gnx.performance.performance.PerformanceVO)"}; tData[tData.length] = row;
			row = {"key":"kr.co.gnx.performance.performance.PerformanceDAO.selectProductGroupMonthAchievementsList(kr.co.gnx.performance.performance.PerformanceVO)","name":"보종별마감업적조회","parent":"kr.co.gnx.performance.performance.PerformanceService.getProductGroupMonthAchievementsList(kr.co.gnx.performance.performance.PerformanceVO)"}; tData[tData.length] = row;
			row = {"key":"/contract/existingcontract/existingContractCar","name":"보유계약(자동차)관리","parent":"ROOT"}; tData[tData.length] = row;
			row = {"key":"/api/getCarDataView.pop","name":"","parent":"/contract/existingcontract/existingContractCar"}; tData[tData.length] = row;
			row = {"key":"kr.co.gnx.api.ApiController.getCarDataView(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, kr.co.gnx.contract.existingcontract.ExistingContractVO)","name":"자동차비교견적데이터수신","parent":"/api/getCarDataView.pop"}; tData[tData.length] = row;
			*/
			
			
			
//console.log("tData================>>>" + JSON.stringify( tData ) );		
			return tData;
		}
	
		// Walk the DOM, starting at document, and return an Array of node data objects representing the DOM tree
		// Typical usage: traverseDom(document.activeElement)
		// The second and third arguments are internal, used when recursing through the DOM
		function traverseDom(node, parentName, dataArray) {
			if (parentName === undefined)
				parentName = null;
			if (dataArray === undefined)
				dataArray = [];
			// skip everything but HTML Elements
			if (!(node instanceof Element))
				return;
			// Ignore the navigation menus
			if (node.id === 'navSide' || node.id === 'navTop')
				return;
			// add this node to the nodeDataArray
			var name = getName(node);
			var data = {
				key : name,
				name : name
			};
			dataArray.push(data);
			// add a link to its parent
			if (parentName !== null) {
				data.parent = parentName;
			}
			// find all children
			var l = node.childNodes.length;
			for (var i = 0; i < l; i++) {
				traverseDom(node.childNodes[i], name, dataArray);
			}
			return dataArray;
		}
	
		// Give every node a unique name
		function getName(node) {
console.log('getName has been called !!!');	
			var n = node.nodeName;
			if (node.id)
				n = n + ' (' + node.id + ')';
			var namenum = n; // make sure the name is unique
			var i = 1;
			while (names[namenum] !== undefined) {
				namenum = n + i;
				i++;
			}
			names[namenum] = node;
			return namenum;
		}
	
		// When a Node is selected, highlight the corresponding HTML element.
		function nodeSelectionChanged(node) {
console.log('nodeSelectionChanged has been called !!! node.font:' + node.name);
			/*
			if (node.isSelected) {
				names[node.data.name].style.backgroundColor = 'lightblue';
			} else {
				names[node.data.name].style.backgroundColor = '';
			}
			*/
		}

	</script>

	<body >
	
	<!-- The DIV needs an explicit size or else we won't see anything. -->
		<div id="myDiagramDiv"
			style="border: 1px solid black; width: 100%; height: 650px; position: relative; -webkit-tap-highlight-color: rgba(255, 255, 255, 0); cursor: auto;">
			
			<!-- Tree -->
			<canvas 
				tabindex="0" 
				width="1235" 
				height="648"
				style="
					position: absolute; 
					top: 0px; 
					left: 0px; 
					z-index: 2;
					user-select: none; 
					touch-action: none; 
					width: 1235px; height: 648px; 
					cursor: auto;
					"
			></canvas>
			
			<!-- 하단 스크롤 바 -->
			<div style="position: absolute; overflow: auto; width: 1246px; height: 648px; z-index: 1;"> 
				<div style="position: absolute; width: 1px; height: 532.661px;"> </div>
			</div>
		</div>


	</body>
</html>