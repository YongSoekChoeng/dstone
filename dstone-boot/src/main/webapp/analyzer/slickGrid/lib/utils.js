		
		function createAddlHeaderRow() {
			var preHeaderPanelElm = grid.getPreHeaderPanel();
			preHeaderPanelElm.innerHTML = '';
			preHeaderPanelElm.className = "slick-header-columns";
			preHeaderPanelElm.style.left = '-1000px';
			preHeaderPanelElm.style.width = `${grid.getHeadersWidth()}px`;
			preHeaderPanelElm.parentElement.classList.add("slick-header");

			var headerColumnWidthDiff = grid.getHeaderColumnWidthDiff();
			var m, headerElm, lastColumnGroup = '', widthTotal = 0;

			for (var i = 0; i < columns.length; i++) {
				m = columns[i];
				if (lastColumnGroup === m.columnGroup && i>0) {
			 		widthTotal += m.width;
			 		headerElm.style.width = `${widthTotal - headerColumnWidthDiff}px`;
				} else {
			  		widthTotal = m.width;
			    	headerElm = document.createElement('div');
			    	headerElm.className = 'ui-state-default slick-header-column';
					headerElm.style.width = `${(m.width || 0) - headerColumnWidthDiff}px`;

			   		const spanElm = document.createElement('span');
			   		spanElm.className='slick-column-name';
			 		spanElm.textContent = m.columnGroup || '';
			      	headerElm.appendChild(spanElm);
			    	preHeaderPanelElm.appendChild(headerElm);
				}
				lastColumnGroup = m.columnGroup;
			}
		}
		