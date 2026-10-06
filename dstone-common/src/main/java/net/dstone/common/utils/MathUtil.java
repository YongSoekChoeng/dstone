package net.dstone.common.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MathUtil {
	
	/**
	 * 두 장소의 위경도로 거리를 구하는 메소드
	 * @param oriLat(현재위치-위도)
	 * @param oriLon(현재위치-경도)
	 * @param tgtLat(목적위치-위도)
	 * @param tgtLog(목적위치-경도)
	 * @return
	 */
	public static double getHaversine(double oriLat, double oriLon, double tgtLat, double tgtLog) {
	    final int R = 6371; // 지구 반지름 (km)
	    double dLat = Math.toRadians(tgtLat - oriLat);
	    double dLon = Math.toRadians(tgtLog - oriLon);
	    double a = Math.sin(dLat/2) * Math.sin(dLat/2)
	             + Math.cos(Math.toRadians(oriLat)) * Math.cos(Math.toRadians(tgtLat))
	             * Math.sin(dLon/2) * Math.sin(dLon/2);
	    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
	    return R * c * 1000; // 미터 단위
	}
	
	/**
	 * 현재장소의 위경도와 비교대상장소목록의 위경도로 거리를 구하여 가장 가까운 혹은 가장 먼 장소정보를 얻는 메소드
	 * @param mode(모드-MAX:거리가 가장 먼 정보 추출, MIN:거리가 가장 가까운 정보 추출)
	 * @param oriLat(현재위치-위도)
	 * @param oriLon(현재위치-경도)
	 * @param tgtList(목적위치리스트-Map에는 tgtLat(목적위치-위도), tgtLog(목적위치-경도)를 반드시 가지고 있어야 한다.)
	 * @return Map tgtList에서 가장 가까운 위치정보를 담고있는 Map을 반환.
	 */
	public static Map<String, Object> getHaversineRank(String mode, double oriLat, double oriLon, List<Map<String, Object>> tgtList) {
		Map<String, Object> map = new HashMap<String, Object>();
		if(tgtList != null && tgtList.size() > 0) {
			
			double minDistance = Double.MAX_VALUE;
			int minDistanceIndex = -1;
			double maxDistance = Double.MIN_NORMAL;
			int maxDistanceIndex = -1;
			
			int calcuIndex = 0;
			for(int i=0; i<tgtList.size(); i++) {
				Map<String, Object> row = tgtList.get(i);
				if( row.containsKey("tgtLat") && row.containsKey("tgtLog") ) {
					double tgtLat = Double.valueOf( row.get("tgtLat")==null?"0":row.get("tgtLat").toString() );
					double tgtLog = Double.valueOf( row.get("tgtLog")==null?"0":row.get("tgtLog").toString() );
					double distance = MathUtil.getHaversine(oriLat, oriLon, tgtLat, tgtLog);
					
					if(calcuIndex==0) {
						minDistance = distance;
						minDistanceIndex = i;
						maxDistance = distance;
						maxDistanceIndex = i;
					}else {			
						if( Double.compare(minDistance, distance) > 0 ) {
							minDistance = distance;
							minDistanceIndex = i;
						}
						if( Double.compare(distance, maxDistance) > 0 ) {
							maxDistance = distance;
							maxDistanceIndex = i;
						}
					}
					calcuIndex++;
				}
			}
			if( "MIN".equals(mode) && minDistanceIndex > -1 ) {
				map = tgtList.get(minDistanceIndex);
			}else if( "MAX".equals(mode) && maxDistanceIndex > -1 ) {
				map = tgtList.get(maxDistanceIndex);
			}
		}
		return map;
	}
	
}
