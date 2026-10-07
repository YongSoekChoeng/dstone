
    $.fn.serializeObject = function() { 
        var o = {}; 
        $(this).find('input[type="hidden"], input[type="text"], input[type="password"], input[type="checkbox"]:checked, input[type="radio"]:checked, select, textarea').each(function() { 
            if ($(this).attr('type') == 'hidden') { //if checkbox is checked do not take the hidden field 
                var $parent = $(this).parent(); 
                var $chb = $parent.find('input[type="checkbox"][name="' + this.name.replace(/\[/g, '\[').replace(/\]/g, '\]') + '\"]'); 
                if ($chb != null) { 
                    if ($chb.prop('checked')) return; 
                } 
            } 
            if (this.name === null || this.name === undefined || this.name === '') return; 
            var elemValue = null; 
            if ($(this).is('select')) elemValue = $(this).find('option:selected').val(); 
            else elemValue = this.value; 
            if (o[this.name] !== undefined) { 
                if (!o[this.name].push) { 
                    o[this.name] = [o[this.name]]; 
                } 
                o[this.name].push(elemValue || ''); 
            } else { 
                o[this.name] = elemValue || ''; 
            } 
        }); 
        return o; 
    }
    
	function openWin(url, target, width, height, option){
		var left  = ($(window).width()/2)-(width/2);
		var top   = ($(window).height()/2)-(height/2);
		var optionStr = "width="+width+", height="+height+", top="+top+", left="+left+" ";
		if(option){
			optionStr = optionStr + option;
		}else{
			optionStr = optionStr + ", toolbar=no, scrollbars=auto, resizable=yes";
		}
		var popup = window.open (url, target, optionStr);
		return popup;
	}