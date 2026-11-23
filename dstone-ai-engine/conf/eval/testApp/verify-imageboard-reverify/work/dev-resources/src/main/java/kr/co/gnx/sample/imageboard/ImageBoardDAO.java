package kr.co.gnx.sample.imageboard;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import kr.co.gnx.base.BaseDAO;

@Repository(value="ImageBoardDAO")
public class ImageBoardDAO extends BaseDAO {

	private static final String imageBoardMapper = "ImageBoard.";	//imageboard-mapper.xml

	public List<Map<String, Object>> selectImageBoardList(ImageBoardVO vo) {
		return getSqlSession().selectList(imageBoardMapper + "selectImageBoardList", vo);
	}

	public Map<String, Object> selectImageBoardView(ImageBoardVO vo) {
		return getSqlSession().selectOne(imageBoardMapper + "selectImageBoardView", vo);
	}

	public int insertImageBoard(ImageBoardVO vo) {
		return getSqlSession().insert(imageBoardMapper + "insertImageBoard", vo);
	}

	public int updateImageBoard(ImageBoardVO vo) {
		return getSqlSession().update(imageBoardMapper + "updateImageBoard", vo);
	}

	public int deleteImageBoard(ImageBoardVO vo) {
		return getSqlSession().update(imageBoardMapper + "deleteImageBoard", vo);
	}
}
