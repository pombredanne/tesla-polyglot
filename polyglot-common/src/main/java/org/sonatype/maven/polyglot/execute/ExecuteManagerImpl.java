/**
 * Copyright (c) 2012 to original author or authors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.sonatype.maven.polyglot.execute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Profile;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.sonatype.maven.polyglot.Constants;
import org.sonatype.maven.polyglot.PolyglotModelManager;

/**
 * Default implementation of the {@link ExecuteManager} component.
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 *
 * @since 0.7
 */
@Component(role = ExecuteManager.class)
public class ExecuteManagerImpl implements ExecuteManager {

  @Requirement
  protected Logger log;

  @Requirement
  protected PolyglotModelManager manager;

  private final Map<String, List<ExecuteTask>> modelTasks = new HashMap<String, List<ExecuteTask>>();

  @Override
  public void register(final Model model, final List<ExecuteTask> tasks) {
    assert model != null;
    assert tasks != null;

    // Need to copy the contents to avoid the elements
    List<ExecuteTask> copy = new ArrayList<ExecuteTask>(tasks.size());
    copy.addAll(tasks);
    modelTasks.put(model.getId(), Collections.unmodifiableList(copy));
  }

  @Override
  public List<ExecuteTask> getTasks(final Model model) {
    assert model != null;

    List<ExecuteTask> tasks = modelTasks.get(model.getId());

    // If we cannot find the model then look for the model where it has been registered with less
    // specificity.
    if (tasks == null) {
      Model inheritingModel = new Model();
      inheritingModel.setArtifactId(model.getArtifactId());
      inheritingModel.setPackaging(model.getPackaging());
      inheritingModel.setVersion(model.getVersion());
      tasks = modelTasks.get(inheritingModel.getId());
    }
    if (tasks == null) {
      Model inheritingModel = new Model();
      inheritingModel.setArtifactId(model.getArtifactId());
      inheritingModel.setGroupId(model.getGroupId());
      inheritingModel.setPackaging(model.getPackaging());
      tasks = modelTasks.get(inheritingModel.getId());
    }
    if (tasks == null) {
      Model inheritingModel = new Model();
      inheritingModel.setArtifactId(model.getArtifactId());
      inheritingModel.setPackaging(model.getPackaging());
      tasks = modelTasks.get(inheritingModel.getId());
    }

    // Well, we've tried our hardest...
    if (tasks == null) {
      return Collections.emptyList();
    }

    return tasks;
  }

  //@Override
  //@Deprecated
  //public void install(final Model model) {
  //  install(model, new HashMap<String, String>());
 // }

  //@Override
  @Override
  public void install(final Model model, final Map<String, ?> options) {
    assert model != null;

    List<ExecuteTask> tasks = getTasks(model);
    if (tasks.isEmpty()) {
      return;
    }

    if (log.isDebugEnabled()) {
      log.debug("Registering tasks for: " + model.getId());
    }

    List<String> goals = Collections.singletonList("execute");

    Map<String, Plugin> plugins = new HashMap<String, Plugin>();

    for (ExecuteTask task : tasks) {
      if (log.isDebugEnabled()) {
        log.debug("Registering task: " + task.getId());
      }

      Plugin plugin = getPlugin(model, task.getProfileId(), plugins);

      String id = task.getId();

      PluginExecution execution = new PluginExecution();
      if (id != null && id.length() > 0) {
        execution.setId(id);
      }
      execution.setPhase(task.getPhase());
      execution.setGoals(goals);

      Xpp3Dom config = new Xpp3Dom("configuration");
      execution.setConfiguration(config);

      if (id != null && id.length() > 0) {
        Xpp3Dom child = new Xpp3Dom("taskId");
        child.setValue(id);
        config.addChild(child);
      }

      if (model.getPomFile() != null) {
        Xpp3Dom nativePom = new Xpp3Dom("nativePom");
        nativePom.setValue(model.getPomFile().getName());
        config.addChild(nativePom);
      }

      plugin.addExecution(execution);
    }

    String flavour = manager.getFlavourFor(options);
    for (Plugin plugin : plugins.values()) {
      Dependency dep = new Dependency();
      dep.setGroupId(Constants.getGroupId());
      dep.setArtifactId(Constants.getArtifactId(flavour));
      dep.setVersion(Constants.getVersion());
      plugin.addDependency(dep);
    }
  }

  private BuildBase getBuild(final Model model, String profileId) {
    if (profileId == null) {
      if (model.getBuild() == null) {
        model.setBuild(new Build());
      }
      return model.getBuild();
    } else {
      for (Profile p : model.getProfiles()) {
        if (profileId.equals(p.getId())) {
          if (p.getBuild() == null) {
            p.setBuild(new Build());
          }
          return p.getBuild();
        }
      }
      Profile profile = new Profile();
      profile.setId(profileId);
      profile.setBuild(new Build());
      model.addProfile(profile);
      return profile.getBuild();
    }
  }

  private Plugin getPlugin(final Model model, String profileId, Map<String, Plugin> plugins) {
    Plugin plugin = plugins.get(profileId);
    if (plugin == null) {
      plugin = new Plugin();
      plugin.setGroupId(Constants.getGroupId());
      plugin.setArtifactId(Constants.getArtifactId("maven-plugin"));
      plugin.setVersion(Constants.getVersion());

      // Do not assume that the existing list is mutable.
      BuildBase build = getBuild(model, profileId);
      List<Plugin> existingPlugins = build.getPlugins();
      List<Plugin> mutablePlugins = new ArrayList<Plugin>(existingPlugins);
      build.setPlugins(mutablePlugins);
      build.addPlugin(plugin);

      plugins.put(profileId, plugin);
    }
    return plugin;
  }
}