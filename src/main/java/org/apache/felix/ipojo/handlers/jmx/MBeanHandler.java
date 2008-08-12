/* 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.felix.ipojo.handlers.jmx;

import java.lang.management.ManagementFactory;
import java.util.Dictionary;
import java.util.Properties;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.felix.ipojo.InstanceManager;
import org.apache.felix.ipojo.PrimitiveHandler;
import org.apache.felix.ipojo.architecture.HandlerDescription;
import org.apache.felix.ipojo.metadata.Element;
import org.apache.felix.ipojo.parser.FieldMetadata;
import org.apache.felix.ipojo.parser.MethodMetadata;
import org.apache.felix.ipojo.parser.PojoMetadata;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * This class implements iPOJO Handler. it builds the dynamic MBean from
 * metadata.xml and exposes it to the MBean Server.
 * 
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
public class MBeanHandler extends PrimitiveHandler {

    /**
     * Name of the MBeanRegistration postDeregister method.
     */
    public static final String POST_DEREGISTER_METH_NAME = "postDeregister";

    /**
     * Name of the MBeanRegistration preDeregister method.
     */
    public static final String PRE_DEREGISTER_METH_NAME = "preDeregister";

    /**
     * Name of the MBeanRegistration postRegister method.
     */
    public static final String POST_REGISTER_METH_NAME = "postRegister";

    /**
     * Name of the MBeanRegistration preRegister method.
     */
    public static final String PRE_REGISTER_METH_NAME = "preRegister";

    /**
     * Name of the global configuration element.
     */
    private static final String JMX_CONFIG_ELT = "config";

    /**
     * Name of the component object full name attribute.
     */
    private static final String JMX_OBJ_NAME_ELT = "objectName";

    /**
     * Name of the component object name domain attribute.
     */
    private static final String JMX_OBJ_NAME_DOMAIN_ELT = "domain";

    /**
     * Name of the component object name attribute.
     */
    private static final String JMX_OBJ_NAME_WO_DOMAIN_ELT = "name";

    /**
     * Name of the attribute indicating if the handler uses MOSGi MBean server.
     */
    private static final String JMX_USES_MOSGI_ELT = "usesMOSGi";

    /**
     * Name of a method element.
     */
    private static final String JMX_METHOD_ELT = "method";

    /**
     * Name of the property or method name attribute.
     */
    private static final String JMX_NAME_ELT = "name";

    /**
     * Name of a method description attribute.
     */
    private static final String JMX_DESCRIPTION_ELT = "description";

    /**
     * Name of a property element.
     */
    private static final String JMX_PROPERTY_ELT = "property";

    /**
     * Name of the field attribute.
     */
    private static final String JMX_FIELD_ELT = "field";

    /**
     * Name of the notification attribute.
     */
    private static final String JMX_NOTIFICATION_ELT = "notification";

    /**
     * Name of the rights attribute.
     */
    private static final String JMX_RIGHTS_ELT = "rights";

    /**
     * InstanceManager: use to store the InstanceManager instance.
     */
    private InstanceManager m_instanceManager;
    /**
     * ServiceRegistration : use to register and unregister the Dynamic MBean.
     */
    private ServiceRegistration m_serviceRegistration;
    /**
     * JmxConfigFieldMap : use to store data when parsing metadata.xml.
     */
    private JmxConfigFieldMap m_jmxConfigFieldMap;
    /**
     * DynamicMBeanImpl : store the Dynamic MBean.
     */
    private DynamicMBeanImpl m_MBean;
    /**
     * String : constant which store the name of the class.
     */
    private String m_namespace = "org.apache.felix.ipojo.handlers.jmx";
    /**
     * Flag used to say if we use MOSGi framework.
     */
    private boolean m_usesMOSGi = false;
    /**
     * ObjectName used to register the MBean.
     */
    private ObjectName m_objectName;
    /**
     * Flag used to say if the MBean is registered.
     */
    private boolean m_registered = false;
    /**
     * object name specified in handler configuration. It can be null.
     */
    private String m_completeObjNameElt;
    /**
     * object name without domain specified in handler configuration. It can be
     * null.
     */
    private String m_objNameWODomainElt;
    
    /**
     * object name domain specified in handler configuration. It can be null.
     */
    private String m_domainElt;
    /**
     * flag representing if the Pojo implements MBeanRegistration interface.
     */
    private boolean m_registerCallbacks;
    /**
     * preRegister method of MBeanRegistration interface. It is null if pojo
     * doesn't implement MBeanRegistration interface.
     */
    private MethodMetadata m_preRegisterMeth;
    /**
     * postRegister method of MBeanRegistration interface. It is null if pojo
     * doesn't implement MBeanRegistration interface.
     */
    private MethodMetadata m_postRegisterMeth;
    /**
     * preDeregister method of MBeanRegistration interface. It is null if pojo
     * doesn't implement MBeanRegistration interface.
     */
    private MethodMetadata m_preDeregisterMeth;
    /**
     * postDeregister method of MBeanRegistration interface. It is null if pojo
     * doesn't implement MBeanRegistration interface.
     */
    private MethodMetadata m_postDeregisterMeth;

    /**
     * configure : construct the structure JmxConfigFieldMap.and the Dynamic
     * Mbean.
     * 
     * @param metadata
     *            Element
     * @param dict
     *            Dictionary
     */
    public void configure(Element metadata, Dictionary dict) {

        PojoMetadata manipulation = getPojoMetadata();

        m_instanceManager = getInstanceManager();

        m_jmxConfigFieldMap = new JmxConfigFieldMap();

        // Build the hashmap
        Element[] mbeans = metadata.getElements(JMX_CONFIG_ELT, m_namespace);

        if (mbeans.length != 1) {
            error("A component must have at most one " + JMX_CONFIG_ELT + ".");
            error("The JMX handler configuration is ignored.");
            return;
        }

        // retrieve kind of MBeanServer to use
        m_usesMOSGi = Boolean.parseBoolean(mbeans[0]
            .getAttribute(JMX_USES_MOSGI_ELT));

        // retrieve object name
        m_completeObjNameElt = mbeans[0].getAttribute(JMX_OBJ_NAME_ELT);
        m_domainElt = mbeans[0].getAttribute(JMX_OBJ_NAME_DOMAIN_ELT);
        m_objNameWODomainElt = mbeans[0]
            .getAttribute(JMX_OBJ_NAME_WO_DOMAIN_ELT);

        // test if Pojo is interested in registration callbacks
        m_registerCallbacks = manipulation
            .isInterfaceImplemented(MBeanRegistration.class.getName());
        if (m_registerCallbacks) {
            // don't need to check that methods exist, the pojo implements
            // MBeanRegistration interface
            String[] preRegisterParams = { MBeanServer.class.getName(),
                    ObjectName.class.getName() };
            m_preRegisterMeth = manipulation.getMethod(PRE_REGISTER_METH_NAME,
                preRegisterParams);

            String[] postRegisterParams = { Boolean.class.getName() };
            m_postRegisterMeth = manipulation.getMethod(
                POST_REGISTER_METH_NAME, postRegisterParams);

            m_preDeregisterMeth = manipulation.getMethod(
                PRE_DEREGISTER_METH_NAME, new String[0]);

            m_postDeregisterMeth = manipulation.getMethod(
                POST_DEREGISTER_METH_NAME, new String[0]);
        }

        // set property
        Element[] attributes = mbeans[0].getElements(JMX_PROPERTY_ELT);
        // String[] fields = new String[attributes.length];
        if (attributes != null) {
            for (int i = 0; attributes != null && i < attributes.length; i++) {
                boolean notif = false;
                String rights;
                String name;
                String field = attributes[i].getAttribute(JMX_FIELD_ELT);

                if (attributes[i].containsAttribute(JMX_NAME_ELT)) {
                    name = attributes[i].getAttribute(JMX_NAME_ELT);
                } else {
                    name = field;
                }
                if (attributes[i].containsAttribute(JMX_RIGHTS_ELT)) {
                    rights = attributes[i].getAttribute(JMX_RIGHTS_ELT);
                } else {
                    rights = "r";
                }

                PropertyField property = new PropertyField(name, field, rights,
                    getTypeFromAttributeField(field, manipulation));

                if (attributes[i].containsAttribute(JMX_NOTIFICATION_ELT)) {
                    notif = Boolean.parseBoolean(attributes[i]
                        .getAttribute(JMX_NOTIFICATION_ELT));
                }

                property.setNotifiable(notif);

                if (notif) {
                    // add the new notifiable property in structure
                    NotificationField notification = new NotificationField(
                        name, this.getClass().getName() + "." + field, null);
                    m_jmxConfigFieldMap.addNotificationFromName(name,
                        notification);
                }
                m_jmxConfigFieldMap.addPropertyFromName(name, property);
                getInstanceManager().register(manipulation.getField(field),
                    this);
                info("property exposed:" + name + " " + field + ":"
                        + getTypeFromAttributeField(field, manipulation) + " "
                        + rights + ", Notif=" + notif);
            }
        }

        // set methods
        Element[] methods = mbeans[0].getElements(JMX_METHOD_ELT);
        for (int i = 0; methods != null && i < methods.length; i++) {
            String name = methods[i].getAttribute(JMX_NAME_ELT);
            String description = null;
            if (methods[i].containsAttribute(JMX_DESCRIPTION_ELT)) {
                description = methods[i].getAttribute(JMX_DESCRIPTION_ELT);
            }

            MethodField[] method = getMethodsFromName(name, manipulation,
                description);

            for (int j = 0; j < method.length; j++) {
                m_jmxConfigFieldMap.addMethodFromName(name, method[j]);

                info("method exposed:" + method[j].getReturnType() + " " + name);
            }
        }

    }

    /**
     * start : register the Dynamic Mbean.
     */
    public void start() {
        // create the corresponding MBean
        if (m_registerCallbacks) {
            m_MBean = new DynamicMBeanWRegisterImpl(m_jmxConfigFieldMap,
                m_instanceManager, m_preRegisterMeth, m_postRegisterMeth,
                m_preDeregisterMeth, m_postDeregisterMeth);
        } else {
            m_MBean = new DynamicMBeanImpl(m_jmxConfigFieldMap,
                m_instanceManager);
        }

        if (m_usesMOSGi) {
            // use whiteboard pattern to register MBean

            if (m_serviceRegistration != null) {
                m_serviceRegistration.unregister();
            }

            // Register the ManagedService
            BundleContext bundleContext = m_instanceManager.getContext();
            Properties properties = new Properties();
            try {
                m_objectName = new ObjectName(getObjectNameString());

                properties.put("jmxagent.objectName", m_objectName.toString());

                m_serviceRegistration = bundleContext.registerService(
                    javax.management.DynamicMBean.class.getName(), m_MBean,
                    properties);

                m_registered = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            try {
                m_objectName = new ObjectName(getObjectNameString());
                ObjectInstance instance = ManagementFactory
                    .getPlatformMBeanServer().registerMBean(m_MBean,
                        m_objectName);

                // we must retrieve object name used to register the MBean.
                // It can have been changed by preRegister method of
                // MBeanRegistration interface.
                if (m_registerCallbacks) {
                    m_objectName = instance.getObjectName();
                }

                m_registered = true;
            } catch (Exception e) {
                error("Registration of MBean failed.", e);
            }
        }
    }

    /**
     * Return the object name of the exposed component.
     * 
     * @return the object name of the exposed component.
     */
    private String getObjectNameString() {
        if (m_completeObjNameElt != null) {
            return m_completeObjNameElt;
        }

        String domain;
        if (m_domainElt != null) {
            domain = m_domainElt;
        } else {
            domain = getPackageName(m_instanceManager.getClassName());
        }

        String name = "type=" + m_instanceManager.getClassName() + ",instance="
                + m_instanceManager.getInstanceName();
        if (m_objNameWODomainElt != null) {
            name = m_objNameWODomainElt;
        }

        StringBuffer sb = new StringBuffer();
        if ((domain != null) && (domain.length() > 0)) {
            sb.append(domain + ":");
        }
        sb.append(name);

        return sb.toString();
    }

    /**
     * Extract the package name from of given type.
     * 
     * @param className
     *            the type.
     * @return the package name of the given type.
     */
    private String getPackageName(String className) {
        String packageName = "";

        int plotIdx = className.lastIndexOf(".");
        if (plotIdx != -1) {
            packageName = className.substring(0, plotIdx);
        }

        return packageName;
    }

    /**
     * stop : unregister the Dynamic Mbean.
     */
    public void stop() {
        if (m_usesMOSGi) {
            if (m_serviceRegistration != null) {
                m_serviceRegistration.unregister();
            }
        } else {
            if (m_objectName != null) {
                try {
                    ManagementFactory.getPlatformMBeanServer().unregisterMBean(
                        m_objectName);
                } catch (Exception e) {
                    error("Unregistration of MBean failed.", e);
                }
                m_objectName = null;
            }
        }

        m_MBean = null;
        m_registered = false;
    }

    /**
     * setterCallback : call when a POJO member is modified externally.
     * 
     * @param pojo
     *            : the POJO object
     * @param fieldName
     *            : name of the modified field
     * @param value
     *            : new value of the field
     */
    public void onSet(Object pojo, String fieldName, Object value) {
        // Check if the field is a configurable property

        PropertyField propertyField = (PropertyField) m_jmxConfigFieldMap
            .getPropertyFromField(fieldName);
        if (propertyField != null) {
            if (propertyField.isNotifiable()) {
                // TODO should send notif only when value has changed to a value
                // different than the last one.
                m_MBean.sendNotification(propertyField.getName() + " changed",
                    propertyField.getName(), propertyField.getType(),
                    propertyField.getValue(), value);
            }
            propertyField.setValue(value);
        }
    }

    /**
     * getterCallback : call when a POJO member is modified by the MBean.
     * 
     * @param pojo
     *            : pojo object.
     * @param fieldName
     *            : name of the modified field
     * @param value
     *            : old value of the field
     * @return : new value of the field
     */
    public Object onGet(Object pojo, String fieldName, Object value) {

        // Check if the field is a configurable property
        PropertyField propertyField = (PropertyField) m_jmxConfigFieldMap
            .getPropertyFromField(fieldName);
        if (propertyField != null) {
            m_instanceManager.onSet(pojo, fieldName, propertyField.getValue());
            return propertyField.getValue();
        }
        // m_instanceManager.onSet(pojo, fieldName, value);
        return value;
    }

    /**
     * getTypeFromAttributeField : get the type from a field name.
     * 
     * @param fieldRequire
     *            : name of the requiered field
     * @param manipulation
     *            : metadata extract from metadata.xml file
     * @return : type of the field or null if it wasn't found
     */
    private static String getTypeFromAttributeField(String fieldRequire,
            PojoMetadata manipulation) {

        FieldMetadata field = manipulation.getField(fieldRequire);
        if (field == null) {
            return null;
        } else {
            return FieldMetadata.getReflectionType(field.getFieldType());
        }
    }

    /**
     * getMethodsFromName : get all the methods available which get this name.
     * 
     * @param methodName
     *            : name of the requiered methods
     * @param manipulation
     *            : metadata extract from metadata.xml file
     * @param description
     *            : description which appears in jmx console
     * @return : array of methods with the right name
     */
    private MethodField[] getMethodsFromName(String methodName,
            PojoMetadata manipulation, String description) {

        MethodMetadata[] fields = manipulation.getMethods(methodName);
        if (fields.length == 0) {
            return null;
        }

        MethodField[] ret = new MethodField[fields.length];

        if (fields.length == 1) {
            ret[0] = new MethodField(fields[0], description);
            return ret;
        } else {
            for (int i = 0; i < fields.length; i++) {
                ret[i] = new MethodField(fields[i], description);
            }
            return ret;
        }
    }

    /**
     * Get the jmx handler description.
     * 
     * @return the jmx handler description.
     * @see org.apache.felix.ipojo.Handler#getDescription()
     */
    public HandlerDescription getDescription() {
        return new JMXHandlerDescription(this);
    }

    /**
     * Return the objectName used to register the MBean. If the MBean is not
     * registered, return an empty string.
     * 
     * @return the objectName used to register the MBean.
     * @see org.apache.felix.ipojo.Handler#getDescription()
     */
    public String getUsedObjectName() {
        if (m_objectName != null) {
            return m_objectName.toString();
        } else {
            return "";
        }
    }

    /**
     * Return true if the MBean is registered.
     * 
     * @return true if the MBean is registered.
     */
    public boolean isRegistered() {
        return m_registered;
    }

    /**
     * Return true if the MBean must be registered thanks to whiteboard pattern
     * of MOSGi.
     * 
     * @return true if the MBean must be registered thanks to whiteboard pattern
     *         of MOSGi.
     */
    public boolean isUsesMOSGi() {
        return m_usesMOSGi;
    }

    /**
     * Return true if the MOSGi framework is present on the OSGi plateforme.
     * 
     * @return true if the MOSGi framework is present on the OSGi plateforme.
     */
    public boolean isMOSGiExists() {
        for (Bundle bundle : m_instanceManager.getContext().getBundles()) {
            String symbolicName = bundle.getSymbolicName();
            if ("org.apache.felix.mosgi.jmx.agent".equals(symbolicName)) {
                return true;
            }
        }

        return false;
    }
}
