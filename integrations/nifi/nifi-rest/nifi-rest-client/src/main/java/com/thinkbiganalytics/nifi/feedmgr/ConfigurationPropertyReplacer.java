package com.thinkbiganalytics.nifi.feedmgr;


import com.thinkbiganalytics.nifi.rest.model.NifiProperty;

import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.web.api.dto.ControllerServiceDTO;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto Inject Property Values stored in the application.properties file
 * 2 use cases are supported
 * 1) store properties in the file starting with the prefix defined in the "PropertyExpressionResolver class"  default = config.
 * 2) store properties in the file starting with "nifi.<PROCESSORTYPE>.<PROPERTY_KEY>   where PROCESSORTYPE and PROPERTY_KEY are all lowercase and the spaces are substituted with underscore
 *
 */
public class ConfigurationPropertyReplacer {

    public static String getProcessorPropertyConfigName(NifiProperty property ) {
        String processorTypeName = "nifi."+(StringUtils.substringAfterLast(property.getProcessorType(), ".") + "." + property.getKey()).toLowerCase().trim().replaceAll(" +", "_");
        return processorTypeName;
    }

    /**
     * This will replace the Map of Properties in the DTO but not persist back to Nifi.  You need to call the rest client to persist the change
     */
    public static void replaceControllerServiceProperties(ControllerServiceDTO controllerServiceDTO, Map<String, String> properties) {
        if (controllerServiceDTO != null) {
            //check both Nifis Internal Key name as well as the Displayname to match the properties
            CaseInsensitiveMap propertyMap = new CaseInsensitiveMap(properties);
            Map<String, String> controllerServiceProperties = controllerServiceDTO.getProperties();

            controllerServiceProperties.entrySet().stream().filter(
                entry -> (propertyMap.containsKey(entry.getKey()) || (controllerServiceDTO.getDescriptors().get(entry.getKey()) != null && propertyMap
                    .containsKey(controllerServiceDTO.getDescriptors().get(entry.getKey()).getDisplayName())))).
                forEach(entry -> {
                    boolean isSensitive = controllerServiceDTO.getDescriptors().get(entry.getKey()).isSensitive();
                    String value = (String) propertyMap.get(entry.getKey());
                    if (StringUtils.isBlank(value)) {
                        value = (String) propertyMap.get(controllerServiceDTO.getDescriptors().get(entry.getKey()).getDisplayName());
                    }
                    if (!isSensitive || (isSensitive && !StringUtils.isBlank(value))) {
                        entry.setValue(value);
                    }

                });
        }
    }

    /**
     *
     * @param property
     * @return
     */
    public static boolean resolveStaticConfigurationProperty(NifiProperty property, Map<String,Object> configProperties){
        String value = property.getValue();
        StringBuffer sb = null;

        if(configProperties != null && !configProperties.isEmpty()) {


            if (StringUtils.isNotBlank(value)) {
                Pattern variablePattern = Pattern.compile("\\$\\{(.*?)\\}");
                Matcher matchVariablePattern = variablePattern.matcher(value);
                while (matchVariablePattern.find()) {
                    if (sb == null) {
                        sb = new StringBuffer();
                    }
                    String group = matchVariablePattern.group();
                    int groupCount = matchVariablePattern.groupCount();
                    if (groupCount == 1) {

                        String variable = matchVariablePattern.group(1);
                        //lookup the variable
                        //first look at configuration properties
                        Object resolvedValue = configProperties.get(variable);
                        if (resolvedValue != null) {
                            matchVariablePattern.appendReplacement(sb, resolvedValue.toString());
                        }
                    }
                }
                if (sb != null) {
                    matchVariablePattern.appendTail(sb);
                }
            }
        }

                if(sb == null) {
                    String key = getProcessorPropertyConfigName(property);

                    Object resolvedValue =  configProperties != null ? configProperties.get(key) : null;
                    if (resolvedValue != null) {
                        sb = new StringBuffer();
                        sb.append(resolvedValue.toString());
                    }

                }
                if(sb != null){
                    property.setValue(StringUtils.trim(sb.toString()));
                }


        return sb != null;
    }

}