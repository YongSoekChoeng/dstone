/*
 * 개발 결과 받기 (실행 관리 화면)
 *
 * 승인 step의 approval.deliverable이 가리키는 결과 파일을 다룬다. 어떤 Workflow인지는 모른다.
 *  - [ZIP으로 다운로드]  바뀐 파일, 삭제 목록, 문서를 받는다(모든 브라우저)
 *  - [로컬 맵핑]         결과 파일의 폴더 → 내 PC의 폴더. 같은 key(프로젝트)면 다시 쓴다
 *  - [로컬에 반영]       비교 목록을 보여 주고, 고른 것만 맵핑한 폴더에 쓴다
 *
 * 로컬 폴더 쓰기는 File System Access API를 쓴다(Chrome·Edge, https 또는 localhost).
 * 폴더 핸들은 localStorage에 담을 수 없어 IndexedDB에 둔다.
 * 해시는 서버(WorkFlowDeliverableService)와 같게 계산한다: 글자 파일은 CRLF를 LF로 바꾼 뒤 SHA-256.
 */
var DstoneAiDeliverable = (function () {

	var DB_NAME = "dstone-ai-deliverable";
	var STORE_MAPPINGS = "mappings";
	var STORE_APPLIED = "applied";

	// 상태별 표시. checked: 처음에 고른 채로 둘지, selectable: 고를 수 있는지
	var STATES = {
		create:   { label: "새 파일", checked: true, selectable: true },
		update:   { label: "수정", checked: true, selectable: true },
		missing:  { label: "로컬에 원본 없음", checked: false, selectable: true },
		conflict: { label: "충돌(로컬에서 고침)", checked: false, selectable: true },
		remove:   { label: "삭제", checked: false, selectable: true },
		removeConflict: { label: "삭제(로컬에서 고친 파일)", checked: false, selectable: true },
		same:     { label: "같음", checked: false, selectable: false },
		gone:     { label: "이미 없음", checked: false, selectable: false },
		unmapped: { label: "맵핑 없음", checked: false, selectable: false },
		overlap:  { label: "같은 로컬 파일에 겹침", checked: false, selectable: false },
		error:    { label: "읽기 실패", checked: false, selectable: false }
	};

	/* ====================================================================== 순수 계산 */

	function isBinary(bytes) {
		return bytes.indexOf(0) >= 0;
	}

	// CRLF → LF. 바이트로 다루므로 글자셋(UTF-8, MS949)에 상관없다
	function toLf(bytes) {
		var out = new Uint8Array(bytes.length);
		var size = 0;
		for (var i = 0; i < bytes.length; i++) {
			if (bytes[i] === 13 && bytes[i + 1] === 10) {
				continue;
			}
			out[size++] = bytes[i];
		}
		return out.subarray(0, size);
	}

	function toCrlf(bytes) {
		var lf = toLf(bytes);
		var out = new Uint8Array(lf.length * 2);
		var size = 0;
		for (var i = 0; i < lf.length; i++) {
			if (lf[i] === 10) {
				out[size++] = 13;
			}
			out[size++] = lf[i];
		}
		return out.subarray(0, size);
	}

	function hasCrlf(bytes) {
		for (var i = 1; i < bytes.length; i++) {
			if (bytes[i] === 10 && bytes[i - 1] === 13) {
				return true;
			}
		}
		return false;
	}

	// 쓸 내용. 글자 파일이고 로컬 파일이 있으면 줄바꿈을 로컬 파일에 맞춘다
	function contentToWrite(incoming, local) {
		if (!local || isBinary(incoming) || isBinary(local) || local.indexOf(10) < 0) {
			return incoming;
		}
		return hasCrlf(local) ? toCrlf(incoming) : toLf(incoming);
	}

	function comparableHash(bytes) {
		var target = isBinary(bytes) ? bytes : toLf(bytes);
		return crypto.subtle.digest("SHA-256", target).then(function (digest) {
			return Array.prototype.map.call(new Uint8Array(digest), function (b) {
				return (b < 16 ? "0" : "") + b.toString(16);
			}).join("");
		});
	}

	function normalizePrefix(prefix) {
		return String(prefix || "").trim().replace(/\\/g, "/").replace(/^\/+|\/+$/g, "");
	}

	function isSafePath(path) {
		return path.split("/").every(function (part) {
			return part !== "" && part !== "." && part !== "..";
		});
	}

	// 폴더 경계로 맞는 줄 가운데 가장 긴 것을 고른다. rest는 그 폴더 아래의 경로
	function mapPath(path, rows) {
		var best = null;
		rows.forEach(function (row, index) {
			var prefix = normalizePrefix(row.prefix);
			if (!row.handle || (prefix !== "" && path.indexOf(prefix + "/") !== 0)) {
				return;
			}
			if (best === null || prefix.length > best.prefix.length) {
				best = { index: index, prefix: prefix, rest: prefix === "" ? path : path.substring(prefix.length + 1), row: row };
			}
		});
		return best;
	}

	// 새 내용(item.hash), 원본(item.baseHash), 지난번에 쓴 것(applied), 로컬(localHash, 없으면 null)로 상태를 정한다
	function classifyFile(item, localHash, applied) {
		if (localHash === null) {
			// 원본에 있던 파일이 로컬에 없으면 폴더를 잘못 골랐을 수 있다
			return item.baseHash && !applied ? "missing" : "create";
		}
		if (localHash === item.hash) {
			return "same";
		}
		return localHash === item.baseHash || localHash === applied ? "update" : "conflict";
	}

	function classifyDeletion(item, localHash, applied) {
		if (localHash === null) {
			return "gone";
		}
		return localHash === item.baseHash || localHash === applied ? "remove" : "removeConflict";
	}

	/* ---------- ZIP(압축 없이 담기). 백업용 ---------- */

	function crc32(bytes) {
		var table = crc32.table;
		if (!table) {
			table = crc32.table = [];
			for (var n = 0; n < 256; n++) {
				var c = n;
				for (var k = 0; k < 8; k++) {
					c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
				}
				table[n] = c >>> 0;
			}
		}
		var crc = 0xffffffff;
		for (var i = 0; i < bytes.length; i++) {
			crc = table[(crc ^ bytes[i]) & 255] ^ (crc >>> 8);
		}
		return (crc ^ 0xffffffff) >>> 0;
	}

	function zipParts(entries) {
		var encoder = new TextEncoder();
		var parts = [];
		var directory = [];
		var offset = 0;
		var now = new Date();
		var dosTime = (now.getHours() << 11) | (now.getMinutes() << 5) | (now.getSeconds() >> 1);
		var dosDate = ((now.getFullYear() - 1980) << 9) | ((now.getMonth() + 1) << 5) | now.getDate();
		function record(size, fields) {
			var view = new DataView(new ArrayBuffer(size));
			fields.forEach(function (field) {
				if (field[1] === 2) {
					view.setUint16(field[0], field[2], true);
				} else {
					view.setUint32(field[0], field[2], true);
				}
			});
			return new Uint8Array(view.buffer);
		}
		entries.forEach(function (entry) {
			var name = encoder.encode(entry.name);
			var crc = crc32(entry.bytes);
			var length = entry.bytes.length;
			// 0x0800: 파일 이름이 UTF-8
			var local = record(30, [[0, 4, 0x04034b50], [4, 2, 20], [6, 2, 0x0800], [10, 2, dosTime], [12, 2, dosDate],
				[14, 4, crc], [18, 4, length], [22, 4, length], [26, 2, name.length]]);
			directory.push(record(46, [[0, 4, 0x02014b50], [4, 2, 20], [6, 2, 20], [8, 2, 0x0800], [12, 2, dosTime], [14, 2, dosDate],
				[16, 4, crc], [20, 4, length], [24, 4, length], [28, 2, name.length], [42, 4, offset]]), name);
			parts.push(local, name, entry.bytes);
			offset += local.length + name.length + length;
		});
		var directorySize = 0;
		directory.forEach(function (part) { directorySize += part.length; });
		var end = record(22, [[0, 4, 0x06054b50], [8, 2, entries.length], [10, 2, entries.length], [12, 4, directorySize], [16, 4, offset]]);
		return parts.concat(directory, [end]);
	}

	/* ====================================================================== 로컬 폴더 */

	// 상대경로의 파일을 읽는다. 없으면 null
	function readLocal(root, rest) {
		var parts = rest.split("/");
		var name = parts.pop();
		var chain = Promise.resolve(root);
		parts.forEach(function (part) {
			chain = chain.then(function (dir) { return dir.getDirectoryHandle(part); });
		});
		return chain
			.then(function (dir) { return dir.getFileHandle(name); })
			.then(function (handle) { return handle.getFile(); })
			.then(function (file) { return file.arrayBuffer(); })
			.then(function (buffer) { return new Uint8Array(buffer); })
			.catch(function (err) {
				if (err && err.name === "NotFoundError") {
					return null;
				}
				throw err;
			});
	}

	function parentOf(root, rest, create) {
		var parts = rest.split("/");
		var name = parts.pop();
		var chain = Promise.resolve(root);
		parts.forEach(function (part) {
			chain = chain.then(function (dir) { return dir.getDirectoryHandle(part, { create: create }); });
		});
		return chain.then(function (dir) { return { dir: dir, name: name }; });
	}

	function writeLocal(root, rest, bytes) {
		return parentOf(root, rest, true)
			.then(function (parent) { return parent.dir.getFileHandle(parent.name, { create: true }); })
			.then(function (handle) { return handle.createWritable(); })
			.then(function (writable) {
				return writable.write(bytes).then(function () { return writable.close(); });
			});
	}

	function removeLocal(root, rest) {
		return parentOf(root, rest, false).then(function (parent) { return parent.dir.removeEntry(parent.name); });
	}

	// 배열을 차례로 처리한다(파일 쓰기를 한꺼번에 하지 않는다)
	function inOrder(items, work) {
		var chain = Promise.resolve();
		items.forEach(function (item, index) {
			chain = chain.then(function () { return work(item, index); });
		});
		return chain;
	}

	// 서로 다른 줄이 같은 폴더를 가리키는지 보고, 줄마다 대표 줄 번호를 돌려준다
	function sameFolderIndex(rows) {
		var result = [];
		return inOrder(rows, function (row, index) {
			result[index] = index;
			return inOrder(rows.slice(0, index), function (other, otherIndex) {
				if (!row.handle || !other.handle || result[index] !== index) {
					return null;
				}
				return row.handle.isSameEntry(other.handle).then(function (same) {
					if (same) {
						result[index] = result[otherIndex];
					}
				});
			});
		}).then(function () { return result; });
	}

	// 결과 파일마다 로컬의 지금 상태를 보고 할 일을 정한다
	function buildPlan(data, rows, applied) {
		var items = data.files.map(function (file) { return { source: file, deletion: false }; })
			.concat(data.deletes.map(function (file) { return { source: file, deletion: true }; }));
		var used = {};
		var folders;
		return sameFolderIndex(rows).then(function (result) {
			folders = result;
			return inOrder(items, function (item) {
				item.path = item.source.path;
				var mapped = isSafePath(item.path) ? mapPath(item.path, rows) : null;
				if (!mapped) {
					item.state = "unmapped";
					return null;
				}
				item.root = mapped.row.handle;
				item.rest = mapped.rest;
				item.target = (mapped.row.alias || mapped.row.handle.name) + "/" + mapped.rest;
				var targetKey = folders[mapped.index] + ":" + mapped.rest;
				if (used[targetKey]) {
					item.state = "overlap";
					return null;
				}
				used[targetKey] = true;
				return readLocal(item.root, item.rest).then(function (local) {
					item.local = local;
					return (local === null ? Promise.resolve(null) : comparableHash(local)).then(function (hash) {
						item.localHash = hash;
						item.state = item.deletion ? classifyDeletion(item.source, hash, applied[item.path])
							: classifyFile(item.source, hash, applied[item.path]);
					});
				}).catch(function (err) {
					item.state = "error";
					item.error = err.message || String(err);
				});
			});
		}).then(function () { return items; });
	}

	// 고른 항목을 반영한다. 미리 본 뒤에 로컬이 바뀐 파일은 건드리지 않는다
	function applyPlan(items, applied) {
		var report = { written: 0, removed: 0, failed: [] };
		return inOrder(items, function (item) {
			if (item.state === "same") {
				applied[item.path] = item.source.hash;
				return null;
			}
			if (!item.selected) {
				return null;
			}
			return readLocal(item.root, item.rest).then(function (local) {
				return (local === null ? Promise.resolve(null) : comparableHash(local)).then(function (hash) {
					if (hash !== item.localHash) {
						throw new Error("비교한 뒤에 로컬 파일이 바뀌었습니다. 다시 반영하세요.");
					}
					if (item.deletion) {
						return removeLocal(item.root, item.rest).then(function () {
							delete applied[item.path];
							report.removed++;
						});
					}
					return writeLocal(item.root, item.rest, contentToWrite(item.bytes, local)).then(function () {
						applied[item.path] = item.source.hash;
						report.written++;
					});
				});
			}).catch(function (err) {
				report.failed.push(item.path + ": " + (err.message || err.name));
			});
		}).then(function () { return report; });
	}

	/* ====================================================================== IndexedDB */

	function openDb() {
		return new Promise(function (resolve, reject) {
			var request = indexedDB.open(DB_NAME, 1);
			request.onupgradeneeded = function () {
				request.result.createObjectStore(STORE_MAPPINGS);
				request.result.createObjectStore(STORE_APPLIED);
			};
			request.onsuccess = function () { resolve(request.result); };
			request.onerror = function () { reject(request.error); };
		});
	}

	function dbRun(store, mode, work) {
		return openDb().then(function (db) {
			return new Promise(function (resolve, reject) {
				var tx = db.transaction(store, mode);
				var request = work(tx.objectStore(store));
				tx.oncomplete = function () { db.close(); resolve(request.result); };
				tx.onerror = tx.onabort = function () { db.close(); reject(tx.error); };
			});
		});
	}

	function dbGet(store, key) {
		return dbRun(store, "readonly", function (s) { return s.get(key); });
	}

	function dbPut(store, key, value) {
		return dbRun(store, "readwrite", function (s) { return s.put(value, key); });
	}

	/* ====================================================================== 화면 */

	var panel, summaryEl, noticeEl, zipBtn, mapBtn, applyBtn, statusEl, modal, modalTitle, modalBody, modalFoot;
	var current = null;      // {executionId, data(목록)}
	var requestKey = null;   // 같은 상태를 다시 묻지 않는다
	var busy = false;

	function localBlockReason() {
		if (!window.isSecureContext) {
			return "로컬 폴더에 반영하려면 https 또는 localhost 주소로 접속해야 합니다. 지금은 ZIP 다운로드만 됩니다.";
		}
		if (!window.showDirectoryPicker) {
			return "이 브라우저는 로컬 폴더 쓰기를 지원하지 않습니다(Chrome·Edge 필요). ZIP 다운로드를 쓰세요.";
		}
		return "";
	}

	function element(tag, className, text) {
		var node = document.createElement(tag);
		if (className) {
			node.className = className;
		}
		if (text !== undefined) {
			node.textContent = text;
		}
		return node;
	}

	function button(label, onClick, plain) {
		var node = element("button", plain ? "kn-plain" : "", label);
		node.type = "button";
		node.addEventListener("click", onClick);
		return node;
	}

	function setup() {
		if (panel) {
			return !!panel;
		}
		panel = document.getElementById("wa-deliverable");
		if (!panel) {
			return false;
		}
		summaryEl = element("div", "wa-dl-summary");
		var buttons = element("div", "wa-dl-buttons");
		zipBtn = button("ZIP으로 다운로드", downloadZip, true);
		mapBtn = button("로컬 맵핑", openMapping, true);
		applyBtn = button("로컬에 반영", openApply);
		buttons.appendChild(zipBtn);
		buttons.appendChild(mapBtn);
		buttons.appendChild(applyBtn);
		noticeEl = element("div", "wa-dl-notice");
		statusEl = element("div", "wa-dl-status");
		statusEl.setAttribute("role", "status");
		panel.appendChild(summaryEl);
		panel.appendChild(buttons);
		panel.appendChild(noticeEl);
		panel.appendChild(statusEl);

		modal = element("div", "wa-dl-modal");
		modal.hidden = true;
		var box = element("div", "wa-dl-box");
		box.setAttribute("role", "dialog");
		box.setAttribute("aria-modal", "true");
		modalTitle = element("div", "wa-dl-title");
		modalBody = element("div", "wa-dl-body");
		modalFoot = element("div", "wa-dl-foot");
		box.appendChild(modalTitle);
		box.appendChild(modalBody);
		box.appendChild(modalFoot);
		modal.appendChild(box);
		document.body.appendChild(modal);
		return true;
	}

	// 실행 상세를 그릴 때마다 부른다
	function show(detail) {
		if (!setup()) {
			return;
		}
		var key = detail.executionId + "|" + detail.status + "|" + detail.updatedAt;
		if (key === requestKey) {
			return;
		}
		requestKey = key;
		if (!current || current.executionId !== detail.executionId) {
			panel.hidden = true;
			statusEl.textContent = "";
			closeModal();
		}
		if (detail.status === "RUNNING" || detail.status === "QUEUED") {
			panel.hidden = true;
			return;
		}
		DstoneAi.api("GET", DstoneAi.executionUrl(detail.executionId, "/deliverable"))
			.then(function (data) {
				if (requestKey !== key) {
					return;
				}
				current = { executionId: detail.executionId, data: data };
				// 아직 승인 step에 오지 않았으면 숨긴다. 왔는데 줄 수 없으면 이유를 보여 준다
				panel.hidden = !data.available && !data.stepId;
				var reason = localBlockReason();
				summaryEl.textContent = data.available
					? "개발 결과 받기 — 파일 " + data.files.length + "개, 삭제 " + data.deletes.length + "개, 문서 " + data.documentCount + "개"
					: "개발 결과 받기 — " + data.reason;
				zipBtn.disabled = !data.available;
				mapBtn.disabled = applyBtn.disabled = !data.available || reason !== "";
				mapBtn.title = applyBtn.title = reason;
				noticeEl.textContent = reason;
			})
			.catch(function (err) {
				// 남의 실행(403) 등
				requestKey = null;
				panel.hidden = false;
				summaryEl.textContent = "개발 결과 받기 — " + err.message;
				zipBtn.disabled = mapBtn.disabled = applyBtn.disabled = true;
				noticeEl.textContent = "";
			});
	}

	function saveBlob(parts, name) {
		var url = URL.createObjectURL(new Blob(parts, { type: "application/zip" }));
		var anchor = element("a");
		anchor.href = url;
		anchor.download = name;
		document.body.appendChild(anchor);
		anchor.click();
		anchor.remove();
		setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
	}

	function downloadZip() {
		var executionId = current.executionId;
		statusEl.textContent = "ZIP을 만드는 중...";
		fetch(DstoneAi.executionUrl(executionId, "/deliverable/zip"))
			.then(function (response) {
				if (!response.ok) {
					return response.json().catch(function () { return {}; }).then(function (body) {
						throw new Error(body.message || "HTTP " + response.status);
					});
				}
				return response.blob();
			})
			.then(function (blob) {
				saveBlob([blob], "deliverable-" + executionId + ".zip");
				statusEl.textContent = "ZIP을 내려받았습니다.";
			})
			.catch(function (err) { statusEl.textContent = "다운로드 실패: " + err.message; });
	}

	function openModal(title) {
		modalTitle.textContent = title;
		modalBody.innerHTML = "";
		modalFoot.innerHTML = "";
		modal.hidden = false;
	}

	function closeModal() {
		if (modal && !busy) {
			modal.hidden = true;
		}
	}

	/* ---------- 로컬 맵핑 ---------- */

	// 결과 파일이 있는 폴더들(입력 칸의 추천 값)
	function foldersOf(paths) {
		var seen = {};
		paths.forEach(function (path) {
			var parts = path.split("/");
			for (var i = 1; i < parts.length; i++) {
				seen[parts.slice(0, i).join("/")] = true;
			}
		});
		return Object.keys(seen).sort();
	}

	function openMapping() {
		var data = current.data;
		var paths = data.files.concat(data.deletes).map(function (file) { return file.path; });
		dbGet(STORE_MAPPINGS, data.key).then(function (saved) {
			var rows = (saved && saved.length ? saved : [{ prefix: "", alias: "", handle: null }]).map(function (row) {
				return { prefix: row.prefix, alias: row.alias, handle: row.handle };
			});
			openModal("로컬 맵핑 — " + data.key);
			modalBody.appendChild(element("p", "wa-dl-help",
				"생성된 파일의 폴더를 내 PC의 폴더에 연결합니다. 고른 폴더 아래에는 '생성된 폴더' 다음 경로만 이어 붙입니다. "
				+ "여러 줄이 맞으면 더 긴 경로가 우선합니다. '생성된 폴더'를 비우면 나머지 전부입니다."));
			var list = element("datalist");
			list.id = "wa-dl-folders";
			foldersOf(paths).forEach(function (folder) {
				var option = element("option");
				option.value = folder;
				list.appendChild(option);
			});
			modalBody.appendChild(list);
			var table = element("table", "wa-dl-table");
			modalBody.appendChild(table);
			var countEl = element("p", "wa-dl-help");
			modalBody.appendChild(countEl);

			function updateCounts(cells) {
				var counts = rows.map(function () { return 0; });
				var unmapped = [];
				paths.forEach(function (path) {
					var mapped = mapPath(path, rows);
					if (mapped) {
						counts[mapped.index]++;
					} else {
						unmapped.push(path);
					}
				});
				cells.forEach(function (cell, index) { cell.textContent = rows[index].handle ? counts[index] + "개" : "-"; });
				countEl.textContent = unmapped.length === 0 ? "모든 파일이 맵핑됐습니다."
					: "맵핑 없는 파일 " + unmapped.length + "개(반영하지 않음): " + unmapped.slice(0, 5).join(", ") + (unmapped.length > 5 ? " …" : "");
			}

			function draw() {
				table.innerHTML = "";
				var head = table.insertRow();
				["생성된 폴더", "로컬 폴더", "별칭(알아보기용)", "파일", ""].forEach(function (name) {
					head.appendChild(element("th", "", name));
				});
				var cells = [];
				rows.forEach(function (row, index) {
					var tr = table.insertRow();
					var prefix = element("input");
					prefix.type = "text";
					prefix.value = row.prefix;
					prefix.placeholder = "(전체)";
					prefix.setAttribute("list", "wa-dl-folders");
					prefix.setAttribute("aria-label", "생성된 폴더");
					prefix.addEventListener("input", function () {
						row.prefix = prefix.value;
						updateCounts(cells);
					});
					tr.insertCell().appendChild(prefix);
					var folderCell = tr.insertCell();
					folderCell.appendChild(element("span", "wa-dl-folder", row.handle ? row.handle.name : "고르지 않음"));
					folderCell.appendChild(button("폴더 선택", function () {
						window.showDirectoryPicker({ mode: "readwrite" }).then(function (handle) {
							row.handle = handle;
							draw();
						}).catch(function (err) {
							if (err.name !== "AbortError") {
								alert("폴더를 고르지 못했습니다: " + err.message);
							}
						});
					}, true));
					var alias = element("input");
					alias.type = "text";
					alias.value = row.alias || "";
					alias.placeholder = "예: D:\\work\\my-web";
					alias.setAttribute("aria-label", "별칭");
					alias.addEventListener("input", function () { row.alias = alias.value; });
					tr.insertCell().appendChild(alias);
					cells.push(tr.insertCell());
					tr.insertCell().appendChild(button("삭제", function () {
						rows.splice(index, 1);
						draw();
					}, true));
				});
				updateCounts(cells);
			}
			draw();

			modalFoot.appendChild(button("줄 추가", function () {
				rows.push({ prefix: "", alias: "", handle: null });
				draw();
			}, true));
			modalFoot.appendChild(element("span", "wa-dl-spacer"));
			modalFoot.appendChild(button("취소", closeModal, true));
			modalFoot.appendChild(button("저장", function () {
				var kept = rows.filter(function (row) { return row.handle; }).map(function (row) {
					return { prefix: normalizePrefix(row.prefix), alias: (row.alias || "").trim(), handle: row.handle };
				});
				var prefixes = kept.map(function (row) { return row.prefix; });
				if (prefixes.some(function (p, i) { return prefixes.indexOf(p) !== i; })) {
					alert("같은 '생성된 폴더'가 두 줄 있습니다.");
					return;
				}
				dbPut(STORE_MAPPINGS, data.key, kept).then(function () {
					closeModal();
					statusEl.textContent = "로컬 맵핑을 저장했습니다(" + kept.length + "줄).";
				}).catch(function (err) { alert("저장하지 못했습니다: " + err.message); });
			}));
		}).catch(function (err) { statusEl.textContent = "맵핑을 읽지 못했습니다: " + err.message; });
	}

	/* ---------- 로컬에 반영 ---------- */

	// 저장해 둔 폴더의 쓰기 권한을 받는다. 브라우저는 클릭 직후에만 권한 창을 띄운다
	function ensurePermission(rows) {
		var options = { mode: "readwrite" };
		return inOrder(rows, function (row) {
			return row.handle.queryPermission(options).then(function (state) {
				if (state === "granted") {
					return null;
				}
				return row.handle.requestPermission(options).then(function (answer) {
					if (answer !== "granted") {
						throw new Error("'" + row.handle.name + "' 폴더의 쓰기 권한이 없습니다. 다시 누르거나 [로컬 맵핑]에서 폴더를 다시 고르세요.");
					}
				});
			});
		});
	}

	function fromBase64(text) {
		var raw = atob(text);
		var bytes = new Uint8Array(raw.length);
		for (var i = 0; i < raw.length; i++) {
			bytes[i] = raw.charCodeAt(i);
		}
		return bytes;
	}

	function openApply() {
		var executionId = current.executionId;
		var key = current.data.key;
		var rows;
		var applied;
		statusEl.textContent = "";
		dbGet(STORE_MAPPINGS, key).then(function (saved) {
			rows = saved || [];
			if (rows.length === 0) {
				openMapping();
				return null;
			}
			return ensurePermission(rows).then(function () {
				statusEl.textContent = "로컬 파일과 비교하는 중...";
				return dbGet(STORE_APPLIED, key);
			}).then(function (record) {
				applied = record || {};
				return DstoneAi.api("GET", DstoneAi.executionUrl(executionId, "/deliverable?content=true"));
			}).then(function (data) {
				if (!data.available) {
					throw new Error(data.reason);
				}
				return buildPlan(data, rows, applied).then(function (items) {
					items.forEach(function (item) {
						item.selected = STATES[item.state].checked;
						if (!item.deletion) {
							item.bytes = fromBase64(item.source.content);
						}
					});
					statusEl.textContent = "";
					showPlan(items, key, applied);
				});
			});
		}).catch(function (err) { statusEl.textContent = "반영하지 못했습니다: " + (err.message || err); });
	}

	function countOf(items, state) {
		return items.filter(function (item) { return item.state === state; }).length;
	}

	function showPlan(items, key, applied) {
		openModal("로컬에 반영 — " + key);
		var modified = items.filter(function (item) { return !item.deletion && item.source.baseHash; });
		if (modified.length > 0 && countOf(modified, "missing") * 2 > modified.length) {
			modalBody.appendChild(element("p", "wa-dl-warning",
				"원본에 있던 파일 대부분이 로컬에 없습니다. [로컬 맵핑]에서 폴더를 한 단계 위나 아래로 잘못 고르지 않았는지 확인하세요."));
		}
		var summary = Object.keys(STATES).filter(function (state) { return countOf(items, state) > 0; })
			.map(function (state) { return STATES[state].label + " " + countOf(items, state); }).join(" · ");
		modalBody.appendChild(element("p", "wa-dl-help", summary + " — 체크한 것만 반영합니다."));
		var table = element("table", "wa-dl-table");
		var head = table.insertRow();
		["", "상태", "생성된 파일", "로컬 위치"].forEach(function (name) { head.appendChild(element("th", "", name)); });
		// 사람이 판단할 것이 위에 오게 한다
		var order = Object.keys(STATES);
		var priority = ["conflict", "removeConflict", "missing", "error", "overlap", "unmapped", "remove", "update", "create", "same", "gone"];
		order.sort(function (a, b) { return priority.indexOf(a) - priority.indexOf(b); });
		var run;
		function updateRun() {
			var count = items.filter(function (item) { return item.selected; }).length;
			run.textContent = "선택한 " + count + "개 반영";
			run.disabled = count === 0;
		}
		order.forEach(function (state) {
			items.filter(function (item) { return item.state === state; }).forEach(function (item) {
				var tr = table.insertRow();
				tr.className = "wa-dl-" + state;
				var check = element("input");
				check.type = "checkbox";
				check.checked = item.selected;
				check.disabled = !STATES[state].selectable;
				check.setAttribute("aria-label", item.path);
				check.addEventListener("change", function () {
					item.selected = check.checked;
					updateRun();
				});
				tr.insertCell().appendChild(check);
				tr.insertCell().textContent = STATES[state].label;
				tr.insertCell().textContent = item.path;
				tr.insertCell().textContent = item.error || item.target || "-";
			});
		});
		modalBody.appendChild(table);

		var backupLabel = element("label", "wa-dl-backup");
		var backup = element("input");
		backup.type = "checkbox";
		backup.checked = true;
		backupLabel.appendChild(backup);
		backupLabel.appendChild(document.createTextNode(" 덮어쓰거나 지우는 파일을 백업 ZIP으로 받기"));
		modalFoot.appendChild(backupLabel);
		modalFoot.appendChild(element("span", "wa-dl-spacer"));
		var cancel = button("취소", closeModal, true);
		modalFoot.appendChild(cancel);
		run = button("", function () {
			busy = true;
			run.disabled = cancel.disabled = true;
			if (backup.checked) {
				var entries = items.filter(function (item) { return item.selected && item.local; })
					.map(function (item) { return { name: item.path, bytes: item.local }; });
				if (entries.length > 0) {
					saveBlob(zipParts(entries), "backup-" + new Date().toISOString().replace(/[:.]/g, "-") + ".zip");
				}
			}
			applyPlan(items, applied).then(function (report) {
				return dbPut(STORE_APPLIED, key, applied).catch(function (err) {
					report.failed.push("반영 기록 저장: " + err.message);
				}).then(function () {
					busy = false;
					showReport(items, report);
				});
			}).catch(function (err) {
				busy = false;
				cancel.disabled = false;
				alert("반영하지 못했습니다: " + err.message);
			});
		});
		modalFoot.appendChild(run);
		updateRun();
	}

	function showReport(items, report) {
		var skipped = items.filter(function (item) { return !item.selected && STATES[item.state].selectable; });
		var text = "반영 " + report.written + "개, 삭제 " + report.removed + "개, 같아서 건너뜀 " + countOf(items, "same") + "개, "
			+ "선택하지 않음 " + skipped.length + "개, 맵핑 없음 " + countOf(items, "unmapped") + "개, 실패 " + report.failed.length + "개";
		openModal(report.failed.length === 0 ? "로컬 반영 완료" : "로컬 반영 완료(일부 실패)");
		modalBody.appendChild(element("p", "", text));
		function list(title, lines) {
			if (lines.length === 0) {
				return;
			}
			modalBody.appendChild(element("p", "wa-dl-help", title));
			var ul = element("ul", "wa-dl-list");
			lines.forEach(function (line) { ul.appendChild(element("li", "", line)); });
			modalBody.appendChild(ul);
		}
		list("실패", report.failed);
		list("선택하지 않아 로컬이 예전 상태인 파일", skipped.map(function (item) { return STATES[item.state].label + " — " + item.path; }));
		modalFoot.appendChild(element("span", "wa-dl-spacer"));
		modalFoot.appendChild(button("닫기", closeModal));
		statusEl.textContent = "로컬 반영: " + text;
	}

	return {
		show: show,
		// 시험에서 화면 없이 계산을 확인한다
		logic: {
			toLf: toLf, toCrlf: toCrlf, contentToWrite: contentToWrite, comparableHash: comparableHash, mapPath: mapPath,
			classifyFile: classifyFile, classifyDeletion: classifyDeletion, buildPlan: buildPlan, applyPlan: applyPlan, zipParts: zipParts
		}
	};
})();
