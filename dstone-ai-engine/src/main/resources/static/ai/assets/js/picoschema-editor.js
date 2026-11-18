/*
 * 짧은 타입 표기(Picoschema)를 입력 칸 양식으로 고치는 편집기입니다.
 * Workflow 디자이너(workflow-designer.js)가 Agent의 input/output과 Workflow의 input에 씁니다.
 *
 * 문법은 dstone-ai-engine의 common.schema.Picoschema와 같습니다.
 *   string, 설명                       값 하나(타입 + 설명)
 *   이름: 타입, 설명                    필드 하나. 이름 끝에 ?를 붙이면 없어도 되는 필드
 *   이름(enum, 설명): [A, B]            고를 수 있는 값 목록
 *   이름(array, 설명): 타입             목록. 항목이 객체면 값 자리에 필드를 적는다
 *   이름(object, 설명): 필드들          객체. 설명이 없으면 이름: 아래에 필드만 적어도 된다
 * 타입 이름은 string, number, integer, boolean, null, any 이고, 괄호 안에는 array, object, enum만 온다.
 *
 * 쓰는 법:
 *   DstonePicoschemaEditor.create(담을 요소, 지금 값(YAML에서 읽은 글자나 맵, 없으면 undefined), {
 *     noneLabel: "없음(…)",    // 적지 않음을 고를 수 있게 할 때 그 이름. 없으면 고를 수 없다
 *     compact: true,           // 좁은 칸(디자이너 오른쪽 설정 칸)이면 한 필드를 두 줄로 놓는다
 *     onChange: function (값) // 고칠 때마다 새 값(글자, 맵, 또는 undefined = 적지 않음)
 *   });
 * 양식으로 나타낼 수 없는 표기(예: (*))가 들어 있으면 [직접 입력]으로 열고 이유를 보여 준다. 값을 망가뜨리지 않는다.
 */
var DstonePicoschemaEditor = (function () {

	var SCALARS = ["string", "number", "integer", "boolean", "null", "any"];
	var TYPES = SCALARS.concat(["enum", "array", "object"]);
	var TYPE_LABELS = {
		string: "string 글자", number: "number 숫자", integer: "integer 정수", boolean: "boolean 참/거짓",
		"null": "null", any: "any 무엇이든", "enum": "enum 고르기", array: "array 목록", object: "object 묶음"
	};
	/** 종류를 적은 필드 이름: 이름, ?(없어도 됨), 맨 끝 괄호 안의 종류와 설명. 엔진의 KINDED_FIELD와 같다 */
	var KINDED_FIELD = /^([\s\S]+?)\s*(\?)?\s*\((array|object|enum)\s*(?:,([\s\S]*))?\)$/;
	/** 종류를 적지 않은 필드 이름: 이름과 끝의 ? */
	var PLAIN_FIELD = /^([\s\S]+?)\s*(\?)?$/;

	/** 양식으로 나타낼 수 없는 표기를 만났을 때 던집니다. 메시지가 곧 화면에 보일 이유입니다. */
	function Unsupported(message) {
		this.message = message;
	}

	/* ------------------------------------------------------------------
	 * Picoschema 값 <-> 편집 모델
	 *   모델: {mode: "none" | "single" | "fields" | "raw", type, description, fields: [필드], raw}
	 *   필드: {name, optional, type, description, enumValues, itemType, itemDescription, children: [필드], open}
	 * ------------------------------------------------------------------ */

	/** "타입, 설명" 글자를 나눕니다. 타입 이름이 아니면 Unsupported. */
	function splitTyped(text, where) {
		var value = String(text);
		var comma = value.indexOf(",");
		var type = (comma < 0 ? value : value.substring(0, comma)).trim();
		var description = comma < 0 ? "" : value.substring(comma + 1).trim();
		if (SCALARS.indexOf(type) < 0) {
			throw new Unsupported(where + "의 타입 '" + type + "'은(는) 알 수 없는 이름입니다(쓸 수 있는 이름 = " + SCALARS.join(", ") + ").");
		}
		return { type: type, description: description };
	}

	function toModel(value) {
		if (value === undefined || value === null) {
			return { mode: "none", type: "string", description: "", fields: [] };
		}
		try {
			if (typeof value === "string") {
				var typed = splitTyped(value, "값");
				return { mode: "single", type: typed.type, description: typed.description, fields: [] };
			}
			if (typeof value === "object" && !Array.isArray(value)) {
				return { mode: "fields", type: "string", description: "", fields: toFields(value, "") };
			}
			throw new Unsupported("값이 글자나 필드 맵이 아닙니다.");
		} catch (e) {
			if (e instanceof Unsupported) {
				return { mode: "raw", raw: rawText(value), reason: e.message, type: "string", description: "", fields: [] };
			}
			throw e;
		}
	}

	function toFields(map, where) {
		var fields = [];
		Object.keys(map).forEach(function (key) {
			var value = map[key];
			if (key.trim() === "(*)") {
				throw new Unsupported("(*)(적지 않은 필드 받기)는 양식으로 나타내지 않습니다.");
			}
			var field = { name: "", optional: false, type: "string", description: "", enumValues: [], itemType: "string", itemDescription: "", children: [], open: true };
			var kinded = KINDED_FIELD.exec(key);
			var at;
			if (kinded) {
				field.name = kinded[1].trim();
				field.optional = !!kinded[2];
				field.type = kinded[3];
				field.description = kinded[4] ? kinded[4].trim() : "";
				at = where + field.name;
				if (field.type === "enum") {
					if (!Array.isArray(value)) {
						throw new Unsupported(at + "(enum)의 값이 목록이 아닙니다.");
					}
					field.enumValues = value.map(String);
				} else if (field.type === "object") {
					if (!value || typeof value !== "object" || Array.isArray(value)) {
						throw new Unsupported(at + "(object) 아래에 필드가 없습니다.");
					}
					field.children = toFields(value, at + ".");
				} else {
					if (typeof value === "string") {
						var item = splitTyped(value, at + "의 항목");
						field.itemType = item.type;
						field.itemDescription = item.description;
					} else if (value && typeof value === "object" && !Array.isArray(value)) {
						field.itemType = "object";
						field.children = toFields(value, at + "[].");
					} else {
						throw new Unsupported(at + "(array)의 항목 모양을 읽을 수 없습니다.");
					}
				}
			} else {
				var plain = PLAIN_FIELD.exec(key);
				field.name = plain[1].trim();
				field.optional = !!plain[2];
				at = where + field.name;
				if (typeof value === "string") {
					var typed = splitTyped(value, at);
					field.type = typed.type;
					field.description = typed.description;
				} else if (value && typeof value === "object" && !Array.isArray(value)) {
					field.type = "object";
					field.children = toFields(value, at + ".");
				} else {
					throw new Unsupported(at + "의 값을 읽을 수 없습니다(타입이 없거나 목록입니다).");
				}
			}
			fields.push(field);
		});
		return fields;
	}

	function typed(type, description) {
		return description ? type + ", " + description : type;
	}

	/** 모델을 Picoschema 값으로 바꿉니다. 적지 않음이면 undefined. */
	function fromModel(model) {
		if (model.mode === "none") {
			return undefined;
		}
		if (model.mode === "single") {
			return typed(model.type, model.description.trim());
		}
		if (model.mode === "raw") {
			return parseRaw(model.raw);
		}
		return fromFields(model.fields);
	}

	function fromFields(fields) {
		var map = {};
		fields.forEach(function (field) {
			var name = field.name.trim();
			if (name === "") {
				return;
			}
			var optional = field.optional ? "?" : "";
			var description = field.description.trim();
			var withDescription = description ? ", " + description : "";
			if (field.type === "enum") {
				map[name + optional + "(enum" + withDescription + ")"] = field.enumValues.slice();
			} else if (field.type === "array") {
				map[name + optional + "(array" + withDescription + ")"] = field.itemType === "object"
					? fromFields(field.children) : typed(field.itemType, field.itemDescription);
			} else if (field.type === "object") {
				map[description ? name + optional + "(object" + withDescription + ")" : name + optional] = fromFields(field.children);
			} else {
				map[name + optional] = typed(field.type, description);
			}
		});
		return map;
	}

	function rawText(value) {
		if (value === undefined || value === null) {
			return "";
		}
		if (typeof value === "object") {
			return jsyaml.dump(value, { lineWidth: -1, noRefs: true, quotingType: "\"" }).replace(/\n$/, "");
		}
		return String(value);
	}

	/** [직접 입력] 칸의 글자를 값으로. 키: 값으로 읽히면 맵, 아니면 글자 그대로 */
	function parseRaw(text) {
		var trimmed = String(text || "").trim();
		if (trimmed === "") {
			return undefined;
		}
		var parsed = jsyaml.load(trimmed);
		if (parsed !== null && typeof parsed === "object") {
			return parsed;
		}
		return trimmed;
	}

	/** 엔진이 다르게 읽을 이름인지 봅니다. 문제가 없으면 null. */
	function nameProblem(name, siblings) {
		var trimmed = name.trim();
		if (trimmed === "") {
			return "이름이 없으면 저장되지 않습니다.";
		}
		if (/\?$/.test(trimmed)) {
			return "이름 끝의 ?는 '없어도 됨'으로 읽힙니다. '필수'를 끄십시오.";
		}
		if (/\(\s*(array|object|enum)\b[^)]*\)$/.test(trimmed)) {
			return "이름 끝의 괄호가 종류로 읽힙니다. 괄호를 빼거나 다른 글자를 붙이십시오.";
		}
		if (trimmed === "(*)") {
			return "(*)는 이름으로 쓸 수 없습니다.";
		}
		var same = 0;
		siblings.forEach(function (other) {
			if (other.name.trim() === trimmed) {
				same++;
			}
		});
		return same > 1 ? "같은 이름이 또 있습니다(뒤의 것만 남습니다)." : null;
	}

	function newField() {
		return { name: "", optional: false, type: "string", description: "", enumValues: [], itemType: "string", itemDescription: "", children: [], open: true };
	}

	function hasChildren(field) {
		return field.type === "object" || (field.type === "array" && field.itemType === "object");
	}

	/* ------------------------------------------------------------------
	 * 화면
	 * ------------------------------------------------------------------ */

	function escapeHtml(value) {
		if (value === null || value === undefined) {
			return "";
		}
		return String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
	}

	function typeOptions(current, list) {
		return list.map(function (type) {
			return "<option value=\"" + type + "\"" + (type === current ? " selected" : "") + ">" + escapeHtml(TYPE_LABELS[type] || type) + "</option>";
		}).join("");
	}

	function create(container, value, options) {
		options = options || {};
		var model = toModel(value);
		/** 경로(필드 위치) → 필드. 화면의 data-path로 필드를 찾는다. 다시 그릴 때마다 새로 채운다 */
		var byPath = {};

		function emit() {
			if (options.onChange) {
				options.onChange(fromModel(model));
			}
		}

		function render() {
			byPath = {};
			var modes = [];
			if (options.noneLabel) {
				modes.push(["none", options.noneLabel]);
			}
			modes.push(["single", "값 하나"], ["fields", "필드 묶음"], ["raw", "직접 입력"]);
			var html = "<div class=\"pe" + (options.compact ? " pe-compact" : "") + "\">"
				+ "<div class=\"pe-head\"><select class=\"pe-mode\">" + modes.map(function (m) {
					return "<option value=\"" + m[0] + "\"" + (m[0] === model.mode ? " selected" : "") + ">" + escapeHtml(m[1]) + "</option>";
				}).join("") + "</select>"
				+ (model.mode === "fields" ? "<span class=\"pe-count\">필드 " + countFields(model.fields) + "개</span>" : "")
				+ "</div>";
			if (model.mode === "single") {
				html += "<div class=\"pe-single\"><select data-pe=\"single-type\">" + typeOptions(model.type, SCALARS) + "</select>"
					+ "<input type=\"text\" data-pe=\"single-desc\" placeholder=\"설명(선택)\" value=\"" + escapeHtml(model.description) + "\" /></div>";
			} else if (model.mode === "fields") {
				html += "<div class=\"pe-fields\">"
					+ (options.compact ? "" : "<div class=\"pe-row pe-row-head\"><span>이름</span><span>타입</span><span>필수</span><span>설명</span><span>값 / 항목</span><span></span></div>")
					+ renderFields(model.fields, "", 0)
					+ "</div>"
					+ "<button type=\"button\" class=\"kn-plain pe-add\" data-pe-act=\"add-root\">+ 필드 추가</button>";
			} else if (model.mode === "raw") {
				html += (model.reason ? "<p class=\"pe-reason\">" + escapeHtml(model.reason) + " 이 칸은 글자로 고칩니다.</p>" : "")
					+ "<textarea class=\"wd-code pe-raw\" data-pe=\"raw\" rows=\"6\" spellcheck=\"false\">" + escapeHtml(model.raw || "") + "</textarea>";
			} else {
				html += "<p class=\"pe-none\">" + escapeHtml(options.noneHint || "적지 않습니다.") + "</p>";
			}
			html += "</div>";
			container.innerHTML = html;
			bind();
		}

		function countFields(fields) {
			var n = 0;
			fields.forEach(function (field) {
				n += 1 + (hasChildren(field) ? countFields(field.children) : 0);
			});
			return n;
		}

		function renderFields(fields, parentPath, depth) {
			var html = "";
			fields.forEach(function (field, index) {
				var path = parentPath + index;
				byPath[path] = { field: field, siblings: fields, index: index };
				var problem = nameProblem(field.name, fields);
				var children = hasChildren(field);
				var extra = "";
				if (field.type === "enum") {
					extra = "<input type=\"text\" data-pe=\"enum\" placeholder=\"값, 값 (쉼표로)\" value=\"" + escapeHtml(field.enumValues.join(", ")) + "\" />";
				} else if (field.type === "array") {
					extra = "<select data-pe=\"item\" title=\"항목 타입\">" + typeOptions(field.itemType, SCALARS.concat(["object"])) + "</select>";
				}
				html += "<div class=\"pe-row" + (problem ? " pe-row-bad" : "") + "\" data-path=\"" + path + "\" style=\"--pe-depth:" + depth + "\">"
					+ "<span class=\"pe-name\">"
					+ (children ? "<button type=\"button\" class=\"pe-toggle\" data-pe-act=\"toggle\" title=\"" + (field.open ? "접기" : "펴기") + "\">" + (field.open ? "▾" : "▸") + "</button>" : "<span class=\"pe-toggle-space\"></span>")
					+ "<input type=\"text\" data-pe=\"name\" placeholder=\"이름\" value=\"" + escapeHtml(field.name) + "\" /></span>"
					+ "<span class=\"pe-type\"><select data-pe=\"type\">" + typeOptions(field.type, TYPES) + "</select></span>"
					+ "<label class=\"pe-req\" title=\"끄면 없어도 되는 필드(이름?)\"><input type=\"checkbox\" data-pe=\"required\"" + (field.optional ? "" : " checked") + " />" + (options.compact ? " 필수" : "") + "</label>"
					+ "<span class=\"pe-desc\"><input type=\"text\" data-pe=\"desc\" placeholder=\"설명\" value=\"" + escapeHtml(field.description) + "\" /></span>"
					+ "<span class=\"pe-extra\">" + extra + "</span>"
					+ "<span class=\"pe-acts\">"
					+ (children ? "<button type=\"button\" class=\"pe-icon\" data-pe-act=\"add-child\" title=\"하위 필드 추가\">＋</button>" : "")
					+ "<button type=\"button\" class=\"pe-icon\" data-pe-act=\"up\" title=\"위로\"" + (index === 0 ? " disabled" : "") + ">↑</button>"
					+ "<button type=\"button\" class=\"pe-icon\" data-pe-act=\"down\" title=\"아래로\"" + (index === fields.length - 1 ? " disabled" : "") + ">↓</button>"
					+ "<button type=\"button\" class=\"pe-icon pe-del\" data-pe-act=\"remove\" title=\"지우기\">✕</button>"
					+ "</span>"
					+ (problem ? "<span class=\"pe-problem\">" + escapeHtml(problem) + "</span>" : "")
					+ "</div>";
				if (children && field.open) {
					html += renderFields(field.children, path + ".", depth + 1);
					if (field.children.length === 0) {
						html += "<div class=\"pe-row pe-row-empty\" style=\"--pe-depth:" + (depth + 1) + "\">하위 필드가 없습니다. ＋ 단추로 더하십시오.</div>";
					}
				}
			});
			return html;
		}

		/** 다시 그리지 않고 이름 경고(빈 이름, 같은 이름 등)만 고칩니다. */
		function refreshProblems() {
			Array.prototype.forEach.call(container.querySelectorAll(".pe-row[data-path]"), function (row) {
				var info = byPath[row.getAttribute("data-path")];
				var problem = nameProblem(info.field.name, info.siblings);
				var span = row.querySelector(".pe-problem");
				if (problem && !span) {
					span = document.createElement("span");
					span.className = "pe-problem";
					row.appendChild(span);
				}
				if (span) {
					span.textContent = problem || "";
					if (!problem) {
						row.removeChild(span);
					}
				}
				row.className = "pe-row" + (problem ? " pe-row-bad" : "");
			});
		}

		/** 화면 요소가 들어 있는 줄의 필드 정보 */
		function rowOf(element) {
			var row = element.closest(".pe-row");
			return row ? byPath[row.getAttribute("data-path")] : null;
		}

		function bind() {
			var mode = container.querySelector(".pe-mode");
			mode.addEventListener("change", function () {
				var next = mode.value;
				if (next === model.mode) {
					return;
				}
				if (model.mode === "raw") {
					// 직접 입력에서 나갈 때는 그 글자를 다시 읽어 양식으로 옮긴다. 읽지 못하면 머문다.
					var parsed;
					try {
						parsed = toModel(parseRaw(model.raw));
					} catch (e) {
						window.alert("YAML로 읽지 못해 바꾸지 않았습니다: " + e.message);
						mode.value = "raw";
						return;
					}
					if (parsed.mode === "raw") {
						window.alert(parsed.reason);
						mode.value = "raw";
						return;
					}
					model.type = parsed.type;
					model.description = parsed.description;
					model.fields = parsed.fields;
					if (next === "fields" && parsed.mode === "single" && model.fields.length === 0) {
						model.fields = [newField()];
					}
				} else if (next === "raw") {
					model.raw = rawText(fromModel(model));
					model.reason = "";
				} else if (next === "fields" && model.fields.length === 0) {
					model.fields = [newField()];
				}
				model.mode = next;
				emit();
				render();
			});
			Array.prototype.forEach.call(container.querySelectorAll("[data-pe]"), function (input) {
				var kind = input.getAttribute("data-pe");
				input.addEventListener("change", function () {
					var info = rowOf(input);
					var structural = false;
					if (kind === "single-type") {
						model.type = input.value;
					} else if (kind === "single-desc") {
						model.description = input.value;
					} else if (kind === "raw") {
						model.raw = input.value;
						try {
							parseRaw(model.raw);
						} catch (e) {
							window.alert("YAML로 읽지 못했습니다: " + e.message);
							return;
						}
					} else if (info) {
						var field = info.field;
						if (kind === "name") {
							// 다시 그리면 다음 칸으로 옮긴 커서가 사라지므로, 이름 경고만 고친다.
							field.name = input.value;
							refreshProblems();
						} else if (kind === "type") {
							field.type = input.value;
							if (hasChildren(field) && field.children.length === 0) {
								field.children = [newField()];
							}
							if (field.type === "enum" && field.enumValues.length === 0) {
								field.enumValues = ["값1", "값2"];
							}
							structural = true;
						} else if (kind === "required") {
							field.optional = !input.checked;
						} else if (kind === "desc") {
							field.description = input.value;
						} else if (kind === "enum") {
							field.enumValues = input.value.split(",").map(function (v) {
								return v.trim();
							}).filter(function (v) {
								return v !== "";
							});
						} else if (kind === "item") {
							field.itemType = input.value;
							if (field.itemType === "object" && field.children.length === 0) {
								field.children = [newField()];
							}
							structural = true;
						}
					}
					emit();
					if (structural) {
						render();
					}
				});
			});
			Array.prototype.forEach.call(container.querySelectorAll("[data-pe-act]"), function (button) {
				button.addEventListener("click", function () {
					var act = button.getAttribute("data-pe-act");
					if (act === "add-root") {
						model.fields.push(newField());
					} else {
						var info = rowOf(button);
						if (!info) {
							return;
						}
						var list = info.siblings;
						var i = info.index;
						if (act === "toggle") {
							info.field.open = !info.field.open;
							render();
							return;
						} else if (act === "add-child") {
							info.field.open = true;
							info.field.children.push(newField());
						} else if (act === "up" && i > 0) {
							list.splice(i - 1, 0, list.splice(i, 1)[0]);
						} else if (act === "down" && i < list.length - 1) {
							list.splice(i + 1, 0, list.splice(i, 1)[0]);
						} else if (act === "remove") {
							if (hasChildren(info.field) && info.field.children.length > 0 && !window.confirm("'" + info.field.name + "'와 그 아래 필드를 모두 지웁니다.")) {
								return;
							}
							list.splice(i, 1);
						}
					}
					emit();
					render();
				});
			});
		}

		render();
		return {
			/** 지금 값(Picoschema 글자나 맵, 적지 않음이면 undefined) */
			value: function () {
				return fromModel(model);
			}
		};
	}

	return {
		create: create,
		/** 시험용: 값 → 편집 모델 → 값 왕복 */
		roundTrip: function (value) {
			return fromModel(toModel(value));
		},
		/** 시험용: 그 값을 어느 모양(none, single, fields, raw)으로 여는지 */
		modeOf: function (value) {
			return toModel(value).mode;
		}
	};
})();
