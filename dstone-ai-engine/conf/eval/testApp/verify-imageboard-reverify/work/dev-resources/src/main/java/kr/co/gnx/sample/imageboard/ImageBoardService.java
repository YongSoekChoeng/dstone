package kr.co.gnx.sample.imageboard;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import kr.co.gnx.base.BaseService;

/**
 * 이미지게시판 서비스
 * 이름이 *Service 라서 context-transaction.xml 포인트컷에 걸린다 - 메서드 하나가 트랜잭션 하나.
 */
@Service
public class ImageBoardService extends BaseService {

	@Autowired
	private ImageBoardDAO imageBoardDAO;

	public List<Map<String, Object>> getImageBoardList(ImageBoardVO vo) {
		return imageBoardDAO.selectImageBoardList(vo);
	}

	public Map<String, Object> getImageBoardView(ImageBoardVO vo) {
		return imageBoardDAO.selectImageBoardView(vo);
	}

	public int insertImageBoard(ImageBoardVO vo) {
		return imageBoardDAO.insertImageBoard(vo);
	}

	public int updateImageBoard(ImageBoardVO vo) {
		return imageBoardDAO.updateImageBoard(vo);
	}

	public int deleteImageBoard(List<ImageBoardVO> models) {
		int cnt = 0;
		for(ImageBoardVO vo : models) {
			cnt += imageBoardDAO.deleteImageBoard(vo);
		}
		return cnt;
	}
}
