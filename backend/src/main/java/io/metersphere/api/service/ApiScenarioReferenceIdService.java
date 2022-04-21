package io.metersphere.api.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import io.metersphere.base.domain.ApiScenarioReferenceId;
import io.metersphere.base.domain.ApiScenarioReferenceIdExample;
import io.metersphere.base.domain.ApiScenarioWithBLOBs;
import io.metersphere.base.mapper.ApiScenarioReferenceIdMapper;
import io.metersphere.base.mapper.ext.ExtApiScenarioReferenceIdMapper;
import io.metersphere.commons.utils.SessionUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

/**
 * @author song.tianyang
 * @Date 2021/7/19 11:33 上午
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class ApiScenarioReferenceIdService {
    @Resource
    private ApiScenarioReferenceIdMapper apiScenarioReferenceIdMapper;
    @Resource
    private ExtApiScenarioReferenceIdMapper extApiScenarioReferenceIdMapper;
    @Resource
    private SqlSessionFactory sqlSessionFactory;

    public List<ApiScenarioReferenceId> findByReferenceIds(List<String> deleteIds) {
        if (CollectionUtils.isEmpty(deleteIds)) {
            return new ArrayList<>(0);
        } else {
            ApiScenarioReferenceIdExample example = new ApiScenarioReferenceIdExample();
            example.createCriteria().andReferenceIdIn(deleteIds);
            return apiScenarioReferenceIdMapper.selectByExample(example);
        }
    }

    public void deleteByScenarioId(String scenarioId) {
        ApiScenarioReferenceIdExample example = new ApiScenarioReferenceIdExample();
        example.createCriteria().andApiScenarioIdEqualTo(scenarioId);
        apiScenarioReferenceIdMapper.deleteByExample(example);
    }

    public void deleteByScenarioIds(List<String> scenarioIds) {
        ApiScenarioReferenceIdExample example = new ApiScenarioReferenceIdExample();
        example.createCriteria().andApiScenarioIdIn(scenarioIds);
        apiScenarioReferenceIdMapper.deleteByExample(example);
    }

    public void saveApiAndScenarioRelation(ApiScenarioWithBLOBs scenario) {
        if (scenario.getId() == null) {
            return;
        }
        this.deleteByScenarioId(scenario.getId());
        List<ApiScenarioReferenceId> savedList = this.getApiAndScenarioRelation(scenario);
        this.insertApiScenarioReferenceIds(savedList);
    }

    public void saveApiAndScenarioRelation(List<ApiScenarioWithBLOBs> scenarios) {
        if (CollectionUtils.isNotEmpty(scenarios)) {
            List<String> idList = new ArrayList<>(scenarios.size());
            LinkedList<ApiScenarioReferenceId> savedList = new LinkedList<>();
            scenarios.forEach(scenario -> {
                if (StringUtils.isNotEmpty(scenario.getId())) {
                    idList.add(scenario.getId());
                    savedList.addAll(this.getApiAndScenarioRelation(scenario));
                }
            });
            if (CollectionUtils.isNotEmpty(idList)) {
                ApiScenarioReferenceIdExample example = new ApiScenarioReferenceIdExample();
                example.createCriteria().andApiScenarioIdIn(idList);
                apiScenarioReferenceIdMapper.deleteByExample(example);
            }
            this.insertApiScenarioReferenceIds(savedList);
        }
    }

    public void insertApiScenarioReferenceIds(List<ApiScenarioReferenceId> list) {
        if (CollectionUtils.isNotEmpty(list)) {
            SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
            ApiScenarioReferenceIdMapper referenceIdMapper = sqlSession.getMapper(ApiScenarioReferenceIdMapper.class);
            for (ApiScenarioReferenceId apiScenarioReferenceId : list) {
                referenceIdMapper.insert(apiScenarioReferenceId);
            }
            sqlSession.flushStatements();
            if (sqlSession != null && sqlSessionFactory != null) {
                SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
            }
        }
    }

    public List<ApiScenarioReferenceId> getApiAndScenarioRelation(ApiScenarioWithBLOBs scenario) {
        List<ApiScenarioReferenceId> returnList = new ArrayList<>();
        Map<String, ApiScenarioReferenceId> referenceIdMap = new HashMap<>();
        if (StringUtils.isNotEmpty(scenario.getScenarioDefinition())) {
            JSONObject jsonObject = JSONObject.parseObject(scenario.getScenarioDefinition(), Feature.DisableSpecialKeyDetect);
            if (!jsonObject.containsKey(MsHashTreeService.HASH_TREE)) {
                return returnList;
            }
            JSONArray hashTree = jsonObject.getJSONArray(MsHashTreeService.HASH_TREE);
            for (int index = 0; index < hashTree.size(); index++) {
                JSONObject item = hashTree.getJSONObject(index);
                if (item == null) {
                    continue;
                }

                if (item.containsKey(MsHashTreeService.ID) && item.containsKey(MsHashTreeService.REFERENCED)) {
                    String url = null;
                    String method = null;
                    if (item.containsKey(MsHashTreeService.PATH) && StringUtils.isNotEmpty(MsHashTreeService.PATH)) {
                        url = item.getString(MsHashTreeService.PATH);
                    } else if (item.containsKey(MsHashTreeService.URL)) {
                        url = item.getString(MsHashTreeService.URL);
                    }
                    if (item.containsKey(MsHashTreeService.METHOD)) {
                        method = item.getString(MsHashTreeService.METHOD);
                    }
                    ApiScenarioReferenceId saveItem = new ApiScenarioReferenceId();
                    saveItem.setId(UUID.randomUUID().toString());
                    saveItem.setApiScenarioId(scenario.getId());
                    saveItem.setReferenceId(item.getString(MsHashTreeService.ID));
                    saveItem.setReferenceType(item.getString(MsHashTreeService.REFERENCED));
                    saveItem.setDataType(item.getString(MsHashTreeService.REF_TYPE));
                    saveItem.setCreateTime(System.currentTimeMillis());
                    saveItem.setCreateUserId(SessionUtils.getUserId());
                    saveItem.setUrl(url);
                    saveItem.setMethod(method);
                    referenceIdMap.put(item.getString(MsHashTreeService.ID), saveItem);
                }
                if (item.containsKey(MsHashTreeService.HASH_TREE)) {
                    referenceIdMap.putAll(this.deepElementRelation(scenario.getId(), item.getJSONArray(MsHashTreeService.HASH_TREE)));
                }
            }
        }
        if (MapUtils.isNotEmpty(referenceIdMap)) {
            returnList.addAll(referenceIdMap.values());
        } else {
            ApiScenarioReferenceId saveItem = new ApiScenarioReferenceId();
            saveItem.setId(UUID.randomUUID().toString());
            saveItem.setApiScenarioId(scenario.getId());
            saveItem.setCreateTime(System.currentTimeMillis());
            saveItem.setCreateUserId(SessionUtils.getUserId());
            returnList.add(saveItem);
        }
        return returnList;
    }

    public Map<String, ApiScenarioReferenceId> deepElementRelation(String scenarioId, JSONArray hashTree) {
        Map<String, ApiScenarioReferenceId> deepRelations = new HashMap<>();
        if (CollectionUtils.isNotEmpty(hashTree)) {
            for (int index = 0; index < hashTree.size(); index++) {
                JSONObject item = hashTree.getJSONObject(index);
                if (item.containsKey(MsHashTreeService.ID) && item.containsKey(MsHashTreeService.REFERENCED)) {
                    String method = null;
                    String url = null;
                    if (item.containsKey(MsHashTreeService.PATH) && StringUtils.isNotEmpty(MsHashTreeService.PATH)) {
                        url = item.getString(MsHashTreeService.PATH);
                    } else if (item.containsKey(MsHashTreeService.URL)) {
                        url = item.getString(MsHashTreeService.URL);
                    }
                    if (item.containsKey(MsHashTreeService.METHOD)) {
                        method = item.getString(MsHashTreeService.METHOD);
                    }
                    ApiScenarioReferenceId saveItem = new ApiScenarioReferenceId();
                    saveItem.setId(UUID.randomUUID().toString());
                    saveItem.setApiScenarioId(scenarioId);
                    saveItem.setReferenceId(item.getString(MsHashTreeService.ID));
                    saveItem.setReferenceType(item.getString(MsHashTreeService.REFERENCED));
                    saveItem.setDataType(item.getString(MsHashTreeService.REF_TYPE));
                    saveItem.setCreateTime(System.currentTimeMillis());
                    saveItem.setCreateUserId(SessionUtils.getUserId());
                    saveItem.setMethod(method);
                    saveItem.setUrl(url);
                    deepRelations.put(item.getString(MsHashTreeService.ID), saveItem);
                }
                if (item.containsKey(MsHashTreeService.HASH_TREE)) {
                    deepRelations.putAll(this.deepElementRelation(scenarioId, item.getJSONArray(MsHashTreeService.HASH_TREE)));
                }
            }
        }
        return deepRelations;
    }

    public List<ApiScenarioReferenceId> findByReferenceIdsAndRefType(List<String> deleteIds, String referenceType) {
        if (CollectionUtils.isEmpty(deleteIds)) {
            return new ArrayList<>(0);
        } else {
            ApiScenarioReferenceIdExample example = new ApiScenarioReferenceIdExample();
            example.createCriteria().andReferenceIdIn(deleteIds).andReferenceTypeEqualTo(referenceType);
            return apiScenarioReferenceIdMapper.selectByExample(example);
        }
    }

    public List<ApiScenarioReferenceId> selectUrlByProjectId(String projectId) {
        return extApiScenarioReferenceIdMapper.selectUrlByProjectId(projectId);
    }
}
