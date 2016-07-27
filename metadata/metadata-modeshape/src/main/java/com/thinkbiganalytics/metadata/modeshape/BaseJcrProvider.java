package com.thinkbiganalytics.metadata.modeshape;

import com.thinkbiganalytics.metadata.api.BaseProvider;
import com.thinkbiganalytics.metadata.modeshape.common.JcrEntity;
import com.thinkbiganalytics.metadata.modeshape.common.JcrObject;
import com.thinkbiganalytics.metadata.modeshape.support.JcrPropertyUtil;
import com.thinkbiganalytics.metadata.modeshape.support.JcrQueryUtil;
import com.thinkbiganalytics.metadata.modeshape.support.JcrTool;
import com.thinkbiganalytics.metadata.modeshape.support.JcrUtil;

import org.modeshape.jcr.api.JcrTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.QueryResult;

/**
 * Created by sr186054 on 6/5/16.
 */
public abstract class BaseJcrProvider<T, PK extends Serializable> implements BaseProvider<T, PK> {
    private static final Logger log = LoggerFactory.getLogger(BaseJcrProvider.class);

    protected Session getSession() {
        return JcrMetadataAccess.getActiveSession();
    }

    protected Class<T> entityClass;

    protected Class<? extends JcrEntity> jcrEntityClass;

    public abstract Class<? extends T> getEntityClass();

    public abstract Class<? extends JcrEntity> getJcrEntityClass();

    /**
     * return the JCR NodeType for this entity (i.e. tba:category, tba:feed)
     */
    public abstract String getNodeType();

    public BaseJcrProvider() {
        this.entityClass = (Class<T>) getEntityClass();
        this.jcrEntityClass = getJcrEntityClass();
    }

    /**
     * Creates a new Entity Node object for a Parent Path, relative Path and node type
     */
    public Node findOrCreateEntityNode(String parentPath, String relPath) {
        Session session = getSession();

        try {
            Node typesNode = session.getNode(parentPath);
            JcrTools tools = new JcrTool();
            Node entNode = tools.findOrCreateChild(typesNode, relPath, getNodeType());
            return entNode;
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to create new entity of type: " + getEntityClass(), e);
        }
    }


    public T findOrCreateEntity(String path, String relPath, Map<String, Object> props) {
        return findOrCreateEntity(path, relPath, props, null);
    }

    public T findOrCreateEntity(String path, String relPath, Map<String, Object> props, Object... constructorArgs) {
        return findOrCreateEntity(path, relPath, getJcrEntityClass(), props, constructorArgs);
    }
    
    public T findOrCreateEntity(String path, String relPath, Class<? extends JcrEntity> entClass) {
        return findOrCreateEntity(path, relPath, entClass, null);
    }
    
    public T findOrCreateEntity(String path, String relPath, Class<? extends JcrEntity> entClass, Map<String, Object> props, Object... constructorArgs) {
        Session session = getSession();
        Node entNode = findOrCreateEntityNode(path, relPath);
        entNode = JcrPropertyUtil.setProperties(session, entNode, props);
        return (T) JcrUtil.createJcrObject(entNode, entClass, constructorArgs);
    }

    public Node getNodeByIdentifier(PK id) {
        try {
            Node node = getSession().getNodeByIdentifier(id.toString());
            return node;
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failure while finding entity by ID: " + id, e);
        }
    }

    @Override
    public T findById(PK id) {
        try {
            Node node = null;
            try {
                node = getSession().getNodeByIdentifier(id.toString());
            } catch (ItemNotFoundException e) {
                //swallow this exception
                // if we dont find the item then return null
            }
            if (node != null) {
                return (T) constructEntity(node);
            } else {
                return null;
            }
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failure while finding entity by ID: " + id, e);
        }
    }

    protected T constructEntity(Node node) {
        @SuppressWarnings("unchecked")
        T entity = (T) JcrUtil.createJcrObject(node, getJcrEntityClass());
        return entity;
    }

    public List<T> findWithExplainPlan(String queryExpression) {

        try {
            org.modeshape.jcr.api.query.Query query = (org.modeshape.jcr.api.query.Query)getSession().getWorkspace().getQueryManager().createQuery(queryExpression, "JCR-SQL2");
            org.modeshape.jcr.api.query.QueryResult result =query.explain();
            String plan = result.getPlan();
            log.info(plan);
          return  find(queryExpression);
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failure while finding entity ", e);
        }

    }

    public List<Node> findNodes(String query) {
        List<Node> entities = new ArrayList<>();
        try {
            QueryResult result = JcrQueryUtil.query(getSession(), query);
            if (result != null) {
                NodeIterator nodeIterator = result.getNodes();
                while (nodeIterator.hasNext()) {
                    Node node = nodeIterator.nextNode();
                    entities.add(node);
                }
            }
            return entities;
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Unable to findAll for Type : " + getNodeType(), e);
        }
    }

    public List<T> find(String query) {
        List<T> entities = new ArrayList<>();
        try {
            QueryResult result = JcrQueryUtil.query(getSession(), query);
            if (result != null) {
                NodeIterator nodeIterator = result.getNodes();
                while (nodeIterator.hasNext()) {
                    Node node = nodeIterator.nextNode();
                    T entity = constructEntity(node);
                    entities.add(entity);
                }
            }
            return entities;
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Unable to findAll for Type : " + getNodeType(), e);
        }
    }

    public T findFirst(String query) {
        try {
            QueryResult result = JcrQueryUtil.query(getSession(), query);
            if (result != null) {
                NodeIterator nodeIterator = result.getNodes();
                if (nodeIterator.hasNext()) {
                    Node node = nodeIterator.nextNode();
                    T entity = constructEntity(node);
                    return entity;
                }
            }
            return null;
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Unable to findAll for Type : " + getNodeType(), e);
        }
    }

    @Override
    public List<T> findAll() {

        String jcrQuery = "SELECT * FROM [" + getNodeType() + "]";
        return find(jcrQuery);

    }

    @Override
    public T create(T t) {
        try {
            getSession().save();
            return t;
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Unable to save session", e);
        }
    }

    @Override
    public T update(T t) {
        try {
            getSession().save();
            return t;
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Unable to save session", e);
        }
    }

    @Override
    public void delete(T t) {
        if (t != null) {
            if (t instanceof JcrObject) {
                JcrObject jcrObject = (JcrObject) t;
                jcrObject.remove();
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    @Override
    public void deleteById(PK id) {
        T item = findById(id);
        delete(item);
    }


}