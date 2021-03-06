package com.buschmais.jqassistant.core.analysis.impl;

import static com.buschmais.jqassistant.core.analysis.api.rule.AbstractRule.DEFAULT_CONCEPT_SEVERITY;
import static com.buschmais.jqassistant.core.analysis.api.rule.AbstractRule.DEFAULT_CONSTRAINT_SEVERITY;

import com.buschmais.jqassistant.core.analysis.api.RuleSetReader;
import com.buschmais.jqassistant.core.analysis.api.rule.*;
import com.buschmais.jqassistant.core.analysis.rules.schema.v1.ConceptType;
import com.buschmais.jqassistant.core.analysis.rules.schema.v1.ConstraintType;
import com.buschmais.jqassistant.core.analysis.rules.schema.v1.GroupType;
import com.buschmais.jqassistant.core.analysis.rules.schema.v1.IncludedRefereceType;
import com.buschmais.jqassistant.core.analysis.rules.schema.v1.JqassistantRules;
import com.buschmais.jqassistant.core.analysis.rules.schema.v1.MetricGroupType;
import com.buschmais.jqassistant.core.analysis.rules.schema.v1.MetricType;
import com.buschmais.jqassistant.core.analysis.rules.schema.v1.ObjectFactory;
import com.buschmais.jqassistant.core.analysis.rules.schema.v1.ParameterDefinitionType;
import com.buschmais.jqassistant.core.analysis.rules.schema.v1.ParameterType;
import com.buschmais.jqassistant.core.analysis.rules.schema.v1.ParameterTypes;
import com.buschmais.jqassistant.core.analysis.rules.schema.v1.QueryTemplateType;
import com.buschmais.jqassistant.core.analysis.rules.schema.v1.ReferenceType;
import com.buschmais.jqassistant.core.analysis.rules.schema.v1.ReferenceableType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.Source;

/**
 * A {@link com.buschmais.jqassistant.core.analysis.api.RuleSetReader}
 * implementation.
 */
public class XmlRuleSetReader implements RuleSetReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmlRuleSetReader.class);

    private static final JAXBContext jaxbContext;

    /**
     * Static constructor.
     */
    static {
        try {
            jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
        } catch (JAXBException e) {
            throw new IllegalArgumentException("Cannot create JAXB context.", e);
        }
    }

    @Override
    public RuleSet read(List<RuleSource> sources) {
        List<JqassistantRules> rules = new ArrayList<>();
        for (RuleSource ruleSource : sources) {
            if (ruleSource.isType(RuleSource.Type.XML)) {
                readXmlSource(rules, ruleSource);
            }
        }
        return convert(rules);
    }

    private void readXmlSource(List<JqassistantRules> rules, RuleSource ruleSource) {
        Source source = ruleSource.toSource();
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            unmarshaller.setSchema(XmlHelper.getSchema("/META-INF/xsd/jqassistant-rules-1.0.xsd"));
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Reading rules descriptor '{}'.", source.getSystemId());
            }
            rules.add(unmarshaller.unmarshal(source, JqassistantRules.class).getValue());
        } catch (JAXBException e) {
            throw new IllegalArgumentException("Cannot read rules from '" + source.getSystemId() + "'.", e);
        }
    }

    /**
     * Converts a list of {@link JqassistantRules} to a {@link RuleSet}.
     *
     * @param rules
     *            The {@link JqassistantRules}.
     *
     * @return The corresponding {@link RuleSet}.
     */
    private RuleSet convert(List<JqassistantRules> rules) {
        Map<String, QueryTemplateType> queryTemplateTypes = new HashMap<>();
        Map<String, ConceptType> conceptTypes = new HashMap<>();
        Map<String, ConstraintType> constraintTypes = new HashMap<>();
        Map<String, GroupType> groupTypes = new HashMap<>();
        Map<String, MetricGroupType> metricGroupTypes = new HashMap<>();
        for (JqassistantRules rule : rules) {
            List<ReferenceableType> queryDefinitionOrConceptOrConstraint = rule.getQueryTemplateOrConceptOrConstraint();
            for (ReferenceableType referenceableType : queryDefinitionOrConceptOrConstraint) {
                String id = referenceableType.getId();
                if (referenceableType instanceof QueryTemplateType) {
                    queryTemplateTypes.put(id, (QueryTemplateType) referenceableType);
                } else if (referenceableType instanceof ConceptType) {
                    conceptTypes.put(id, (ConceptType) referenceableType);
                }
                if (referenceableType instanceof ConstraintType) {
                    constraintTypes.put(id, (ConstraintType) referenceableType);
                }
                if (referenceableType instanceof GroupType) {
                    groupTypes.put(id, (GroupType) referenceableType);
                }
                if (referenceableType instanceof MetricGroupType) {
                    metricGroupTypes.put(id, (MetricGroupType) referenceableType);
                }
            }
        }
        RuleSet ruleSet = new RuleSet();
        readConcepts(queryTemplateTypes, conceptTypes, ruleSet);
        readConstraints(queryTemplateTypes, conceptTypes, constraintTypes, ruleSet);
        readGroups(conceptTypes, constraintTypes, groupTypes, ruleSet);
        readMetricGroups(metricGroupTypes, ruleSet);
        return ruleSet;
    }

    /**
     * Reads {@link MetricGroupType}s and converts them to {@link MetricGroup}s.
     * @param ruleSet The {@link RuleSet}.
     */
    private void readMetricGroups(Map<String, MetricGroupType> metricGroupTypes, RuleSet ruleSet) {

        for (MetricGroupType metricGroupType : metricGroupTypes.values()) {

            MetricGroup metricGroup = new MetricGroup();
            metricGroup.setId(metricGroupType.getId());
            metricGroup.setDescription(metricGroupType.getDescription());

            for (MetricType metricType : metricGroupType.getMetric()) {

                Metric metric = new Metric();
                metric.setId(metricType.getId());
                metric.setDescription(metricType.getDescription());

                Query query = new Query();
                query.setCypher(metricType.getCypher());
                for (ParameterDefinitionType parameterDefinitionType : metricType.getParameter()) {
                    // put null here, the value will be added if the query is executed
                    query.getParameters().put(parameterDefinitionType.getName(), null);
                }
                metric.setQuery(query);

                for (ReferenceType referenceType : metricType.getRequiresConcept()) {
                    Concept concept = ruleSet.getConcepts().get(referenceType.getRefId());
                    if (concept == null) {
                        throw new IllegalArgumentException("Cannot resolve required concept for metric " + metric.getId() + " and concept " + referenceType.getRefId());
                    }
                    metric.getRequiresConcepts().add(concept);
                }

                metricGroup.addMetric(metric);
            }

            ruleSet.getMetricGroups().put(metricGroup.getId(), metricGroup);
        }
    }

    /**
     * Reads {@link ConceptType}s and converts them to {@link Concept}s.
     *
     * @param queryTemplateTypes
     *            The {@link QueryTemplateType}s.
     * @param conceptTypes
     *            The {@link ConceptType}s.
     * @param ruleSet
     *            The {@link RuleSet}.
     */
    private void readConcepts(Map<String, QueryTemplateType> queryTemplateTypes, Map<String, ConceptType> conceptTypes, RuleSet ruleSet) {
        for (ConceptType conceptType : conceptTypes.values()) {
            Concept concept = getOrCreateConcept(conceptType.getId(), ruleSet.getConcepts());
            concept.setDescription(conceptType.getDescription());
            // Use default severity; if none configured
            Severity severity = conceptType.getSeverity() == null ? DEFAULT_CONCEPT_SEVERITY : Severity.fromValue(conceptType.getSeverity().value());
            concept.setSeverity(severity);
            if (conceptType.getUseQueryDefinition() != null) {
                concept.setQuery(createQueryFromDefinition(conceptType.getUseQueryDefinition().getRefId(), conceptType.getParameter(), queryTemplateTypes));
            } else {
                concept.setQuery(createQuery(conceptType.getCypher(), conceptType.getParameter()));
            }
            concept.setRequiresConcepts(getRequiredConcepts(conceptType.getRequiresConcept(), conceptTypes, ruleSet));
        }
    }

    /**
     * Reads {@link ConstraintType}s and converts them to {@link Constraint}s.
     *
     * @param queryTemplateTypes
     *            The {@link QueryTemplateType}s.
     * @param conceptTypes
     *            The {@link ConceptType}s.
     * @param constraintTypes
     *            The {@link ConstraintType}s.
     * @param ruleSet
     *            The {@link RuleSet}.
     */
    private void readConstraints(Map<String, QueryTemplateType> queryTemplateTypes, Map<String, ConceptType> conceptTypes,
            Map<String, ConstraintType> constraintTypes, RuleSet ruleSet) {
        for (ConstraintType constraintType : constraintTypes.values()) {
            Constraint constraint = getOrCreateConstraint(constraintType.getId(), ruleSet.getConstraints());
            constraint.setDescription(constraintType.getDescription());
            // Use default severity; if none configured
            Severity severity = constraintType.getSeverity() == null ? DEFAULT_CONSTRAINT_SEVERITY : Severity.fromValue(constraintType.getSeverity().value());
            constraint.setSeverity(severity);
            if (constraintType.getUseQueryDefinition() != null) {
                constraint.setQuery(createQueryFromDefinition(constraintType.getUseQueryDefinition().getRefId(), constraintType.getParameter(),
                        queryTemplateTypes));
            } else {
                constraint.setQuery(createQuery(constraintType.getCypher(), constraintType.getParameter()));
            }
            constraint.setRequiresConcepts(getRequiredConcepts(constraintType.getRequiresConcept(), conceptTypes, ruleSet));
        }
    }

    /**
     * Reads {@link GroupType}s and converts them to
     * {@link com.buschmais.jqassistant.core.analysis.api.rule.Group}s.
     *
     * @param constraintTypes
     *            The {@link ConstraintType}s.
     * @param groupTypes
     *            The {@link GroupType}s.
     * @param ruleSet
     *            The {@link RuleSet}.
     */
    private void readGroups(Map<String, ConceptType> conceptTypes, Map<String, ConstraintType> constraintTypes, Map<String, GroupType> groupTypes,
            RuleSet ruleSet) {
        for (GroupType groupType : groupTypes.values()) {
            Group group = getOrCreateGroup(groupType.getId(), ruleSet.getGroups());
            for (IncludedRefereceType referenceType : groupType.getIncludeConcept()) {
                ConceptType includedConceptType = conceptTypes.get(referenceType.getRefId());
                if (includedConceptType == null) {
                    ruleSet.getMissingConcepts().add(referenceType.getRefId());
                } else {
                    Concept concept = getOrCreateConcept(referenceType.getRefId(), ruleSet.getConcepts());
                    // override the default severity
                    if (referenceType.getSeverity() != null) {
                        concept.setSeverity(Severity.fromValue(referenceType.getSeverity().value()));
                    }
                    group.getConcepts().add(concept);                }
            }
            for (IncludedRefereceType referenceType : groupType.getIncludeConstraint()) {
                ConstraintType constraintType = constraintTypes.get(referenceType.getRefId());
                if (constraintType == null) {
                    ruleSet.getMissingConstraints().add(referenceType.getRefId());
                } else {
                    Constraint constraint = getOrCreateConstraint(referenceType.getRefId(), ruleSet.getConstraints());
                    // override the default severity
                    if (referenceType.getSeverity() != null) {
                        constraint.setSeverity(Severity.fromValue(referenceType.getSeverity().value()));
                    }
                    group.getConstraints().add(constraint);
                }
            }
            for (ReferenceType referenceType : groupType.getIncludeGroup()) {
                GroupType includedGroupType = groupTypes.get(referenceType.getRefId());
                if (includedGroupType == null) {
                    ruleSet.getMissingGroups().add(referenceType.getRefId());
                } else {
                    group.getGroups().add(getOrCreateGroup(referenceType.getRefId(), ruleSet.getGroups()));
                }
            }
        }
    }

    /**
     * Gets a {@link Concept} from the cache or create a new instance if it does
     * not exist yet.
     *
     * @param id
     *            The id.
     * @param concepts
     *            The {@link Concept}s.
     *
     * @return The {@link Concept}.
     */
    private Concept getOrCreateConcept(String id, Map<String, Concept> concepts) {
        Concept concept = concepts.get(id);
        if (concept == null) {
            concept = new Concept();
            concept.setId(id);
            concepts.put(id, concept);
        }
        return concept;
    }

    /**
     * Gets a {@link Constraint} from the cache or create a new instance if it
     * does not exist yet.
     *
     * @param id
     *            The id.
     * @param constraints
     *            The {@link Constraint}s.
     *
     * @return The {@link Constraint}.
     */
    private Constraint getOrCreateConstraint(String id, Map<String, Constraint> constraints) {
        Constraint constraint = constraints.get(id);
        if (constraint == null) {
            constraint = new Constraint();
            constraint.setId(id);
            constraints.put(id, constraint);
        }
        return constraint;
    }

    /**
     * Gets a {@link com.buschmais.jqassistant.core.analysis.api.rule.Group}
     * from the cache or create a new instance if it does not exist yet.
     *
     * @param id
     *            The id.
     * @param groups
     *            The
     *            {@link com.buschmais.jqassistant.core.analysis.api.rule.Group}
     *            s.
     *
     * @return The
     *         {@link com.buschmais.jqassistant.core.analysis.api.rule.Group}.
     */
    private Group getOrCreateGroup(String id, Map<String, Group> groups) {
        Group group = groups.get(id);
        if (group == null) {
            group = new Group();
            group.setId(id);
            groups.put(id, group);
        }
        return group;
    }

    /**
     * Resolves the required {@link Concept}s for a given {@link ReferenceType}.
     *
     * @param referenceTypes
     *            The {@link ReferenceType}s.
     * @param conceptTypes
     *            The {@link ConceptType}s.
     * @param ruleSet
     *            The {@link RuleSet}.
     *
     * @return The required {@link Concept}s.
     */
    private Set<Concept> getRequiredConcepts(List<ReferenceType> referenceTypes, Map<String, ConceptType> conceptTypes, RuleSet ruleSet) {
        Set<Concept> requiredConcepts = new HashSet<Concept>();
        for (ReferenceType referenceType : referenceTypes) {
            ConceptType requiredConceptType = conceptTypes.get(referenceType.getRefId());
            if (requiredConceptType == null) {
                throw new IllegalArgumentException("Cannot resolve required concept: " + referenceType.getRefId());
            }
            requiredConcepts.add(getOrCreateConcept(referenceType.getRefId(), ruleSet.getConcepts()));
        }
        return requiredConcepts;
    }

    /**
     * Creates a {@link Query}.
     *
     * @param cypher
     *            The CYPHER expression.
     * @param parameterTypes
     *            The {@link ParameterType}s.
     *
     * @return The {@link Query}.
     */
    private Query createQuery(String cypher, List<ParameterType> parameterTypes) {
        Query query = new Query();
        query.setCypher(cypher);
        for (ParameterType parameterType : parameterTypes) {
            query.getParameters().put(parameterType.getName(), getValue(parameterType.getType(), parameterType.getValue()));
        }
        return query;
    }

    /**
     * Creates a {@link Query} from a {@link QueryTemplateType} identified by
     * its id.
     *
     * @param id
     *            The id of the {@link QueryTemplateType}.
     * @param parameterTypes
     *            The {@link ParameterType}s.
     * @param QueryTemplateTypes
     *            The {@link QueryTemplateType}s.
     *
     * @return The {@link Query}.
     */
    private Query createQueryFromDefinition(String id, List<ParameterType> parameterTypes, Map<String, QueryTemplateType> QueryTemplateTypes) {
        QueryTemplateType QueryTemplateType = QueryTemplateTypes.get(id);
        if (QueryTemplateType == null) {
            throw new IllegalArgumentException("Cannot resolve used query definition: " + id);
        }
        return createQuery(QueryTemplateType.getCypher(), parameterTypes);
    }

    /**
     * Get a parameter value by its string representation and types.
     *
     * @param type
     *            The {@link ParameterType}.
     * @param stringValue
     *            The string representation.
     *
     * @return The parameter value.
     */
    private Object getValue(ParameterTypes type, String stringValue) {
        Object value;
        switch (type) {
        case INT:
            value = Integer.valueOf(stringValue);
            break;
        case STRING:
            value = stringValue;
            break;
        default:
            throw new IllegalArgumentException("Unsupported parameter types: " + type);
        }
        return value;
    }
}
