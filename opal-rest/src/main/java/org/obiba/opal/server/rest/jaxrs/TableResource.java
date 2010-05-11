/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.opal.server.rest.jaxrs;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.json.JSONObject;
import org.obiba.magma.Value;
import org.obiba.magma.ValueSet;
import org.obiba.magma.ValueTable;
import org.obiba.magma.Variable;
import org.obiba.magma.VariableEntity;
import org.obiba.magma.VariableValueSource;
import org.obiba.magma.support.VariableEntityBean;
import org.obiba.magma.xstream.XStreamValueSet;
import org.obiba.opal.server.rest.model.RestVariable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class TableResource {

  private final ValueTable valueTable;

  public TableResource(ValueTable valueTable) {
    this.valueTable = valueTable;
  }

  @GET
  @Produces( { "application/xml", "application/json" })
  public Map<String, String> details() {
    return ImmutableMap.of("name", valueTable.getName(), "entityType", valueTable.getEntityType());
  }

  @GET
  @Path("/variables")
  @Produces( { "application/xml", "application/json" })
  public Set<RestVariable> getVariables() {
    return ImmutableSet.copyOf(Iterables.transform(valueTable.getVariables(), new Function<Variable, RestVariable>() {

      @Override
      public RestVariable apply(Variable from) {
        return new RestVariable(UriBuilder.fromPath("/").path(DatasourceResource.class).path(DatasourceResource.class, "getTable").path(TableResource.class, "getVariable").build(valueTable.getDatasource().getName(), valueTable.getName(), from.getName()), from);
      }

    }));
  }

  @GET
  @Path("/entities")
  @Produces( { "application/xml", "application/json" })
  public Set<String> getEntities() {
    return ImmutableSet.copyOf(Iterables.transform(valueTable.getValueSets(), new Function<ValueSet, String>() {
      @Override
      public String apply(ValueSet from) {
        return from.getVariableEntity().getIdentifier();
      }
    }));
  }

  @GET
  @Path("/valueSet/{identifier}")
  @Produces("application/xml")
  public XStreamValueSet getValueSet(@PathParam("identifier") String identifier) {
    VariableEntity entity = new VariableEntityBean(this.valueTable.getEntityType(), identifier);
    ValueSet valueSet = this.valueTable.getValueSet(entity);
    XStreamValueSet xvs = new XStreamValueSet(this.valueTable.getName(), entity);
    for(Variable variable : this.valueTable.getVariables()) {
      Value value = this.valueTable.getValue(variable, valueSet);
      xvs.setValue(variable, value);
    }
    return xvs;
  }

  @GET
  @Path("/values.json")
  @Produces("application/json")
  public Response getValuesAsJson(@QueryParam("v") List<String> variables) {
    return Response.ok(new JSONObject(readValues(variables)).toString()).build();
  }

  @GET
  @Path("/values.xml")
  @Produces("application/xml")
  public Map<String, List<Object>> getValuesAsXml(@QueryParam("v") List<String> variables) {
    return readValues(variables);
  }

  @Path("/variable/{variable}")
  public VariableResource getVariable(@PathParam("variable") String name) {
    return getVariableResource(valueTable.getVariableValueSource(name));
  }

  @Bean
  @Scope("request")
  public VariableResource getVariableResource(VariableValueSource source) {
    return new VariableResource(this.valueTable, source);
  }

  private Map<String, List<Object>> readValues(List<String> variables) {
    Map<String, List<Object>> response = new LinkedHashMap<String, List<Object>>();

    if(variables == null || variables.size() == 0) {
      variables = ImmutableList.copyOf(Iterables.transform(valueTable.getVariables(), new Function<Variable, String>() {
        @Override
        public String apply(Variable from) {
          return from.getName();
        }
      }));
    }

    for(String name : variables) {
      response.put(name, new LinkedList<Object>());
    }

    for(ValueSet vs : valueTable.getValueSets()) {
      for(String name : response.keySet()) {
        response.get(name).add(valueTable.getVariableValueSource(name).getValue(vs).getValue());
      }
    }
    return response;
  }
}
