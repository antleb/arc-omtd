package eu.openminted.registry.core.service;

import eu.openminted.registry.core.dao.ResourceDao;
import eu.openminted.registry.core.dao.ResourceTypeDao;
import eu.openminted.registry.core.domain.Resource;
import eu.openminted.registry.core.domain.ResourceType;
import eu.openminted.registry.core.domain.index.IndexedField;
import eu.openminted.registry.core.index.IndexMapper;
import eu.openminted.registry.core.index.IndexMapperFactory;
import eu.openminted.registry.core.validation.ResourceValidator;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service("resourceService")
@Transactional
public class ResourceServiceImpl implements ResourceService {

    private static Logger logger = Logger.getLogger(ResourceServiceImpl.class);
    @Autowired
    private ResourceDao resourceDao;
    @Autowired
    private ResourceTypeDao resourceTypeDao;
    @Autowired
    private IndexMapperFactory indexMapperFactory;
    @Autowired
    private ResourceValidator resourceValidator;

    public ResourceServiceImpl() {

    }

    @Override
    public Resource getResource(String resourceType, String id) {
        return resourceDao.getResource(resourceType, id);
    }

    @Override
    public List<Resource> getResource(ResourceType resourceType) {
        return resourceDao.getResource(resourceType);
    }

    @Override
    public List<Resource> getResource(ResourceType resourceType, int from, int to) {
        return resourceDao.getResource(resourceType, from, to);
    }

    @Override
    public List<Resource> getResource(int from, int to) {
        return resourceDao.getResource(from, to);
    }

    @Override
    public List<Resource> getResource() {
        return resourceDao.getResource();
    }

    @Override
    public Resource addResource(Resource resource) throws ServiceException {
        if (resource.getPayloadUrl() != null ^ resource.getPayload() != null) {
            resource.setCreationDate(new Date());
            resource.setModificationDate(new Date());
            resource.setPayloadFormat(resourceTypeDao.getResourceType(resource.getResourceType()).getPayloadType());
        } else {
            throw new ServiceException("Payload and PayloadUrl conflict : neither set or both set");
        }
        long start_time = System.nanoTime();
        Boolean response = checkValid(resource);
        long end_time = System.nanoTime();
        double difference = (end_time - start_time) / 1e6;
        logger.info("Checking validy of xml in "+difference+"ms");
        if (response) {
            resource.setId(UUID.randomUUID().toString());

            try {
                start_time = System.nanoTime();
                resource.setIndexedFields(getIndexedFields(resource));
                end_time = System.nanoTime();
                difference = (end_time - start_time) / 1e6;
                logger.info("Indexed fields exported in "+ difference+"ms");
                logger.debug("indexed fields: " + resource.getIndexedFields().size());
                for (IndexedField indexedField : resource.getIndexedFields())
                    indexedField.setResource(resource);

                resourceDao.addResource(resource);
            } catch (Exception e) {
                logger.error("Error saving resource", e);
                throw new ServiceException(e);
            }
        }

        return resource;
    }

    @Override
    public Resource updateResource(Resource resource) throws ServiceException {
        resource.setIndexedFields(getIndexedFields(resource));
        resource.setModificationDate(new Date());
        for (IndexedField indexedField : resource.getIndexedFields()) {
            indexedField.setResource(resource);
        }
        Boolean response = checkValid(resource);
        if (response) {
            resourceDao.updateResource(resource);
        }
        return resource;
    }

    @Override
    public void deleteResource(String id) {
        long start_time = System.nanoTime();
        resourceDao.deleteResource(id);
        long end_time = System.nanoTime();
        double difference = (end_time - start_time) / 1e6;

        logger.info("Resource deleted in: "+difference + "ms from DB");

    }

    private List<IndexedField> getIndexedFields(Resource resource) {

        ResourceType resourceType = resourceTypeDao.getResourceType(resource.getResourceType());
        IndexMapper indexMapper = null;
        try {
            indexMapper = indexMapperFactory.createIndexMapper(resourceType);
        } catch (Exception e) {
            logger.error("Error extracting fields", e);
            throw new ServiceException(e);
        }

        return indexMapper.getValues(resource.getPayload(), resourceType);
    }

    public ResourceDao getResourceDao() {
        return resourceDao;
    }

    public void setResourceDao(ResourceDao resourceDao) {
        this.resourceDao = resourceDao;
    }

    public ResourceTypeDao getResourceTypeDao() {
        return resourceTypeDao;
    }

    public void setResourceTypeDao(ResourceTypeDao resourceTypeDao) {
        this.resourceTypeDao = resourceTypeDao;
    }

    private Boolean checkValid(Resource resource) {
        ResourceType resourceType = resourceTypeDao.getResourceType(resource.getResourceType());

        if (resourceType != null) {
            if (resourceType.getPayloadType().equals(resource.getPayloadFormat())) {
                if (resourceType.getPayloadType().equals("xml")) {
                    //validate xml
                    Boolean output = resourceValidator.validateXML(resource.getResourceType(), resource.getPayload());
                    if (output) {
                        resource.setPayload(resource.getPayload());
                    } else {
                        throw new ServiceException("XML and XSD mismatch");
                    }
                } else if (resourceType.getPayloadType().equals("json")) {

                    Boolean output = resourceValidator.validateJSON(resourceType.getSchema(), resource.getPayload());

                    if (output) {
                        resource.setPayload(resource.getPayload());
                    } else {
                        throw new ServiceException("JSON and Schema mismatch");
                    }
                } else {
                    //payload type not supported
                    throw new ServiceException("type not supported");
                }
            } else {
                //payload and schema format do not match, we cant validate
                throw new ServiceException("payload and schema format are different");
            }
        } else {
            //resource type not found
            throw new ServiceException("resource type not found");
        }

        return true;
    }
}

