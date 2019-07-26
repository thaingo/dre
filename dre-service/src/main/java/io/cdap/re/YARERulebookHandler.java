/*
 *
 * Copyright © 2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.re;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.cdap.cdap.api.annotation.TransactionControl;
import io.cdap.cdap.api.annotation.TransactionPolicy;
import io.cdap.cdap.api.service.http.AbstractSystemHttpServiceHandler;
import io.cdap.cdap.api.service.http.HttpServiceRequest;
import io.cdap.cdap.api.service.http.HttpServiceResponder;
import io.cdap.cdap.spi.data.transaction.TransactionRunners;
import org.apache.commons.jexl3.JexlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

/**
 * This class {@link YARERulebookHandler} provides rules and rulebooks management service.
 */
public class YARERulebookHandler extends AbstractSystemHttpServiceHandler {

  private static final Logger LOG = LoggerFactory.getLogger(YARERulebookHandler.class);
  private static final Gson GSON = new Gson();

  @GET
  @Path("health")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void healthCheck(HttpServiceRequest request, HttpServiceResponder responder) {
    responder.sendStatus(HttpURLConnection.HTTP_OK);
  }

  /**
   * This API request is for validating the 'when' clause specified in the expression.
   *
   * @param request to gather information of the request.
   * @param responder to respond to the service request.
   */
  @POST
  @Path("validate-when")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void validateWhen(HttpServiceRequest request, HttpServiceResponder responder) {
    try {
      ServiceUtils.success(responder, "Valid when clause");
    } catch (JexlException e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_BAD_REQUEST, e.getMessage());
    } catch (Exception e) {
      ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR, e.getMessage());
    }
  }

  @POST
  @Path("rules")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void create(HttpServiceRequest request, HttpServiceResponder responder) {
    TransactionRunners.run(getContext(), context -> {
      try {
        RequestExtractor handler = new RequestExtractor(request);
        String content = handler.getContent(StandardCharsets.UTF_8);
        RuleRequest rule = GSON.fromJson(content, RuleRequest.class);
        RulesDB rulesDB = RulesDB.get(context);
        rulesDB.createRule(rule);

        JsonObject response = new JsonObject();
        response.addProperty("status", HttpURLConnection.HTTP_OK);
        response.addProperty("message", String.format("Successfully created rule '%s'.", rule.getId()));
        response.addProperty("count", 1);

        JsonArray values = new JsonArray();
        values.add(new JsonPrimitive(rule.getId()));

        response.add("values", values);

        ServiceUtils.sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
      } catch (RuleAlreadyExistsException e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
      } catch (Exception e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                           String.format("Unexpected error while creating rule. Please check your request. %s",
                                         e.getMessage())
        );
      }
    });
  }

  @GET
  @Path("rules")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void rules(HttpServiceRequest request, HttpServiceResponder responder) {
    TransactionRunners.run(getContext(), context -> {
      try {
        RulesDB rulesDB = RulesDB.get(context);
        List<Map<String, Object>> rules = rulesDB.rules();

        JsonObject response = new JsonObject();
        response.addProperty("status", HttpURLConnection.HTTP_OK);
        response.addProperty("message", "Successfully listed rules, testing .");
        response.addProperty("count", rules.size());
        response.add("values", GSON.toJsonTree(rules));

        ServiceUtils.sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
      } catch (Exception e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                           String.format("Unexpected error while listing rules. Please check your request. %s",
                                         e.getMessage())
        );
      }
    });
  }

  @PUT
  @Path("rules/{rule-id}")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void update(HttpServiceRequest request, HttpServiceResponder responder, @PathParam("rule-id") String id) {
    TransactionRunners.run(getContext(), context -> {
      try {
        RequestExtractor handler = new RequestExtractor(request);
        String content = handler.getContent(StandardCharsets.UTF_8);
        RuleRequest rule = GSON.fromJson(content, RuleRequest.class);
        RulesDB rulesDB = RulesDB.get(context);
        rulesDB.updateRule(id, rule);

        JsonObject response = new JsonObject();
        response.addProperty("status", HttpURLConnection.HTTP_OK);
        response.addProperty("message", String.format("Successfully updated rule '%s'.", id));
        response.addProperty("count", 1);

        JsonArray values = new JsonArray();
        values.add(new JsonPrimitive(id));

        response.add("values", values);

        ServiceUtils.sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
      } catch (RuleNotFoundException e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
      } catch (Exception e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                           String.format("Unexpected error while updating rule. Please check your request. %s",
                                         e.getMessage())
        );
      }
    });
  }

  @GET
  @Path("rules/{rule-id}")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void retrieve(HttpServiceRequest request, HttpServiceResponder responder,
                       @PathParam("rule-id") String id, @QueryParam("format") String format) {
    TransactionRunners.run(getContext(), context -> {
      try {
        RulesDB rulesDB = RulesDB.get(context);
        Map<String, Object> result = rulesDB.retrieveRule(id);

        JsonObject response = new JsonObject();
        response.addProperty("status", HttpURLConnection.HTTP_OK);
        response.addProperty("message", String.format("Successfully retrieved rule '%s'.", id));
        response.addProperty("count", 1);

        if (format == null || format.equalsIgnoreCase("json")) {
          response.add("values", GSON.toJsonTree(result));
        } else {
          JsonArray array = new JsonArray();
          array.add(new JsonPrimitive(rulesDB.retrieveUsingRuleTemplate(id)));
          response.add("values", array);
        }

        ServiceUtils.sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
      } catch (RuleNotFoundException e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
      } catch (Exception e) {
        LOG.debug("Error", e);
        ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                           String.format("Unexpected error while retrieving rule. Please check your request. %s",
                                         e.getMessage())
        );
      }
    });
  }

  @DELETE
  @Path("rules/{rule-id}")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void delete(HttpServiceRequest request, HttpServiceResponder responder,
                     @PathParam("rule-id") String id) {
    TransactionRunners.run(getContext(), context -> {
      try {
        RulesDB rulesDB = RulesDB.get(context);
        rulesDB.deleteRule(id);

        ServiceUtils.success(responder, String.format("Successfully deleted rule '%s'", id));
      } catch (RuleNotFoundException e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
      } catch (Exception e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                           String.format("Unexpected error while deleting the rule. " +
                                           "Please check your request. %s", e.getMessage())
        );
      }
    });
  }

  @POST
  @Path("rulebooks")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void createRb(HttpServiceRequest request, HttpServiceResponder responder) {
    TransactionRunners.run(getContext(), context -> {
      try {
        RequestExtractor handler = new RequestExtractor(request);
        String content = handler.getContent(StandardCharsets.UTF_8);
        RulesDB rulesDB = RulesDB.get(context);
        String id;

        if (handler.isContentType("application/json")) {
          RulebookRequest rb = GSON.fromJson(content, RulebookRequest.class);
          rulesDB.createRulebook(rb);
          id = rb.getId();
        } else if (handler.isContentType("application/rules-engine")) {
          Reader reader = new StringReader(content);
          Compiler compiler = new RulebookCompiler();
          Rulebook rulebook = compiler.compile(reader);
          rulesDB.createRulebook(rulebook);
          id = rulebook.getName();
        } else {
          String header = handler.getHeader(RequestExtractor.CONTENT_TYPE, "");
          ServiceUtils.error(responder, HttpURLConnection.HTTP_BAD_REQUEST, "Unsupported content type " + header + ".");

          return;
        }

        JsonObject response = new JsonObject();
        response.addProperty("status", HttpURLConnection.HTTP_OK);
        response.addProperty("message", String.format("Successfully created rulebook '%s'.", id));
        response.addProperty("count", 1);

        JsonArray values = new JsonArray();
        values.add(new JsonPrimitive(id));

        response.add("values", values);

        ServiceUtils.sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
      } catch (RulebookAlreadyExistsException e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
      } catch (Exception e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                           String.format("Unexpected error while creating rulebook. " +
                                           "Please check your request. %s", e.getMessage())
        );
      }
    });
  }

  @GET
  @Path("rulebooks")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void rulebooks(HttpServiceRequest request, HttpServiceResponder responder) {
    TransactionRunners.run(getContext(), context -> {
      try {
        RulesDB rulesDB = RulesDB.get(context);
        List<Map<String, Object>> rulebooks = rulesDB.rulebooks();

        JsonObject response = new JsonObject();
        response.addProperty("status", HttpURLConnection.HTTP_OK);
        response.addProperty("message", "Successfully listed rulebooks.");
        response.addProperty("count", rulebooks.size());
        response.add("values", GSON.toJsonTree(rulebooks));

        ServiceUtils.sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
      } catch (Exception e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                           String.format("Unable to list all rulebooks. %s", e.getMessage())
        );
      }
    });
  }

  @PUT
  @Path("rulebooks/{rulebook-id}")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void updateRb(HttpServiceRequest request, HttpServiceResponder responder,
                       @PathParam("rulebook-id") String id) {
    TransactionRunners.run(getContext(), context -> {
      try {
        RequestExtractor handler = new RequestExtractor(request);
        String content = handler.getContent(StandardCharsets.UTF_8);
        RulebookRequest rulebook = GSON.fromJson(content, RulebookRequest.class);
        RulesDB rulesDB = RulesDB.get(context);
        rulesDB.updateRulebook(id, rulebook);

        JsonObject response = new JsonObject();
        response.addProperty("status", HttpURLConnection.HTTP_OK);
        response.addProperty("message", String.format("Successfully updated rule '%s'.", id));
        response.addProperty("count", 1);

        JsonArray values = new JsonArray();
        values.add(new JsonPrimitive(id));

        response.add("values", values);

        ServiceUtils.sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
      } catch (Exception e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                           String.format("Unable to update rulebook. %s", e.getMessage())
        );
      }
    });
  }

  @GET
  @Path("rulebooks/{rulebook-id}")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void retrieveRb(HttpServiceRequest request, HttpServiceResponder responder,
                         @PathParam("rulebook-id") String id) {
    TransactionRunners.run(getContext(), context -> {
      try {
        RulesDB rulesDB = RulesDB.get(context);
        String rulebookString = rulesDB.generateRulebook(id);

        JsonObject response = new JsonObject();
        response.addProperty("status", HttpURLConnection.HTTP_OK);
        response.addProperty("message", String.format("Successfully generated rulebook '%s'.", id));
        response.addProperty("count", 1);

        JsonArray values = new JsonArray();
        values.add(new JsonPrimitive(rulebookString));

        response.add("values", values);

        ServiceUtils.sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
      } catch (RuleNotFoundException | RulebookNotFoundException e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
      } catch (Exception e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                           String.format("Unable to retrieve rulebook. %s", e.getMessage())
        );
      }
    });
  }

  @GET
  @Path("rulebooks/{rulebook-id}/rules")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void retrieveRbRules(HttpServiceRequest request, HttpServiceResponder responder,
                              @PathParam("rulebook-id") String id) {
    TransactionRunners.run(getContext(), context -> {
      try {
        RulesDB rulesDB = RulesDB.get(context);
        JsonArray rules = rulesDB.getRulebookRules(id);

        JsonObject response = new JsonObject();
        response.addProperty("status", HttpURLConnection.HTTP_OK);
        response.addProperty("message", String.format("Successfully listed rules for the rulebook '%s'.", id));
        response.addProperty("count", rules.size());
        response.add("values", rules);

        ServiceUtils.sendJson(responder, HttpURLConnection.HTTP_OK, response.toString());
      } catch (RulebookNotFoundException e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
      } catch (Exception e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                           String.format("Unable to retrieve rules for rulebook. %s", e.getMessage())
        );
      }
    });
  }

  @DELETE
  @Path("rulebooks/{rulebook-id}")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void deleteRb(HttpServiceRequest request, HttpServiceResponder responder,
                       @PathParam("rulebook-id") String id) {
    TransactionRunners.run(getContext(), context -> {
      try {
        RulesDB rulesDB = RulesDB.get(context);
        rulesDB.deleteRulebook(id);

        ServiceUtils.success(responder, String.format("Successfully deleted rulebook '%s'", id));
      } catch (RulebookNotFoundException e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
      } catch (Exception e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                           String.format("Unable to delete rulebook. %s", e.getMessage())
        );
      }
    });
  }

  @PUT
  @Path("rulebooks/{rulebook-id}/rules/{rule-id}")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void addRuleToRb(HttpServiceRequest request, HttpServiceResponder responder,
                          @PathParam("rulebook-id") String rbId, @PathParam("rule-id") String id) {
    TransactionRunners.run(getContext(), context -> {
      try {
        RulesDB rulesDB = RulesDB.get(context);
        rulesDB.addRuleToRulebook(rbId, id);

        ServiceUtils.success(responder, String.format("Successfully added rule '%s' to rulebook '%s'", id, rbId));
      } catch (RulebookNotFoundException | RuleNotFoundException e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
      } catch (Exception e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                           String.format("Unable to add rule to rulebook. %s", e.getMessage())
        );
      }
    });
  }

  @DELETE
  @Path("rulebooks/{rulebook-id}/rules/{rule-id}")
  @TransactionPolicy(value = TransactionControl.EXPLICIT)
  public void deleteRuleFromRb(HttpServiceRequest request, HttpServiceResponder responder,
                               @PathParam("rulebook-id") String rbId, @PathParam("rule-id") String id) {
    TransactionRunners.run(getContext(), context -> {
      try {
        RulesDB rulesDB = RulesDB.get(context);
        rulesDB.removeRuleFromRulebook(rbId, id);

        ServiceUtils.success(responder, String.format("Successfully removed rule '%s' to rulebook '%s'", id, rbId));
      } catch (RulebookNotFoundException | RuleNotFoundException e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
      } catch (Exception e) {
        ServiceUtils.error(responder, HttpURLConnection.HTTP_INTERNAL_ERROR,
                           String.format("Unable to remove rule from rulebook. %s", e.getMessage())
        );
      }
    });
  }

}