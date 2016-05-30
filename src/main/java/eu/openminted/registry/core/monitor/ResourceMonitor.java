package eu.openminted.registry.core.monitor;

import java.util.List;

import eu.openminted.registry.core.dao.ResourceDao;
import eu.openminted.registry.core.domain.Resource;
import eu.openminted.registry.core.domain.ResourceType;
import eu.openminted.registry.core.service.ResourceService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import eu.openminted.registry.core.dao.ResourceDao;
import eu.openminted.registry.core.domain.Resource;

/**
 * Created by antleb on 5/26/16.
 */
@Aspect
@Component
public class ResourceMonitor {

	@Autowired(required = false)
	private List<ResourceListener> resourceListeners;

	@Autowired (required = false)
	private List<ResourceTypeListener> resourceTypeListeners;

	@Autowired
	private ResourceDao resourceDao;

	@Around("execution (* eu.openminted.registry.core.service.ResourceService.addResource(eu.openminted.registry.core.domain.Resource)) && args(resource)")
	public void resourceAdded(ProceedingJoinPoint pjp, Resource resource) throws Throwable {

		try {
			pjp.proceed();

			if (resourceListeners != null)
				for (ResourceListener listener:resourceListeners)
					listener.resourceAdded(resource);

		} catch (Throwable throwable) {
			throw throwable;
		}
	}

	@Around("execution (* eu.openminted.registry.core.service.ResourceService.updateResource(eu.openminted.registry.core.domain.Resource)) && args(resource)")
	public void resourceUpdated(ProceedingJoinPoint pjp, Resource resource) throws Throwable {

		try {
			Resource previous = resourceDao.getResource(resource.getResourceType(), resource.getId());

			pjp.proceed();

			if (resourceListeners != null)
				for (ResourceListener listener:resourceListeners)
					listener.resourceUpdated(previous, resource);

		} catch (Throwable throwable) {
			throw throwable;
		}
	}

	@Around(("execution (* eu.openminted.registry.core.service.ResourceService.deleteResource(java.lang.String)) && args(resourceId)"))
	public void resourceDeleted(ProceedingJoinPoint pjp, String resourceId) throws Throwable {

		try {
			Resource previous = resourceDao.getResource(null, resourceId);

			pjp.proceed();

			if (resourceListeners != null)
				for (ResourceListener listener:resourceListeners)
					listener.resourceDeleted(previous);

		} catch (Throwable throwable) {
			throw throwable;
		}
	}

	@Around("execution (* eu.openminted.registry.core.service.ResourceTypeService.addResourceType(eu.openminted.registry.core.domain.ResourceType)) && args(resourceType)")
	public void resourceTypeAdded(ProceedingJoinPoint pjp, ResourceType resourceType) throws Throwable {

		try {
			pjp.proceed();

			if (resourceTypeListeners != null)
				for (ResourceTypeListener listener:resourceTypeListeners)
					listener.resourceTypeAdded(resourceType);

		} catch (Throwable throwable) {
			throw throwable;
		}
	}
}
