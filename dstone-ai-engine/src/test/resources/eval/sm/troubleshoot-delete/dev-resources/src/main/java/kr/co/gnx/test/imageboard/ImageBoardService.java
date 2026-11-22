package kr.co.gnx.test.imageboard;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import kr.co.gnx.base.BaseService;
import kr.co.gnx.comm.util.CommUtil;
import kr.co.gnx.config.Constants.UPLOADS;
import kr.co.gnx.system.file.FileService;
import kr.co.gnx.system.file.FileVO;

/**
 * 이미지 게시판 서비스
 * 이름이 *Service 라서 context-transaction.xml 포인트컷에 걸린다 - 메서드 하나가 트랜잭션 하나.
 */
@Service
public class ImageBoardService extends BaseService {
	private static final Logger logger = LoggerFactory.getLogger(ImageBoardService.class);

	@Autowired
	private ImageBoardDAO imageBoardDAO;

	@Autowired
	private FileService fileService;

	public List<Map<String, Object>> getImageBoardList(ImageBoardVO vo) {
		return imageBoardDAO.selectImageBoardList(vo);
	}

	public Map<String, Object> getImageBoardView(ImageBoardVO vo) {
		return imageBoardDAO.selectImageBoardView(vo);
	}

	/**
	 * 등록/수정. 파일이 있으면 이미지 저장 후 URL을 VO에 채운다.
	 */
	public int saveImageBoard(MultipartFile file, ImageBoardVO vo) throws Exception {
		if (file != null && !file.isEmpty()) {
			FileVO fileVO = fileService.imageSave(file, UPLOADS.UPIMAGE);
			vo.setImage_url(fileVO.getFile_url());
			vo.setThumb_url(makeThumbnail(fileVO));
		}
		if (CommUtil.isEmpty(vo.getSeq())) {
			return imageBoardDAO.insertImageBoard(vo);
		} else {
			return imageBoardDAO.updateImageBoard(vo);
		}
	}

	public int deleteImageBoard(List<Long> seqList) {
		int cnt = 0;
		for (Long seq : seqList) {
			ImageBoardVO vo = new ImageBoardVO();
			vo.setSeq(seq.toString());
			cnt += imageBoardDAO.deleteImageBoard(vo);
		}
		return cnt;
	}

	/**
	 * 원본 이미지를 그리드 썸네일 컬럼(폭 100)에 맞춰 60x60으로 리사이즈한 썸네일을 생성.
	 * 생성 실패 시 원본 URL을 돌려준다.
	 */
	private String makeThumbnail(FileVO fileVO) {
		int size = 60; // 그리드 썸네일 컬럼(폭 100)을 감안해 자체계산
		try {
			BufferedImage img = ImageIO.read(new File(fileVO.getFile_path()));
			BufferedImage thumb = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = thumb.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(img, 0, 0, size, size, null);
			g.dispose();
			String dir = fileVO.getFile_path().substring(0, fileVO.getFile_path().lastIndexOf(File.separatorChar));
			String name = "thumb_" + fileVO.getFile_no() + "." + fileVO.getFile_format();
			ImageIO.write(thumb, fileVO.getFile_format(), new File(dir + File.separator + name));
			return fileVO.getFile_url().substring(0, fileVO.getFile_url().lastIndexOf("/")) + "/" + name;
		} catch (Exception e) {
			logger.warn("썸네일 생성 실패, 원본 표시: " + e.getMessage());
			return fileVO.getFile_url();
		}
	}
}
