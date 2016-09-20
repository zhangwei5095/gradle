/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.play.plugins.ide

import org.gradle.play.integtest.fixtures.PlayApp
import org.gradle.play.integtest.fixtures.app.AdvancedPlayApp

class PlayIdeaPluginAdvancedIntegrationTest extends PlayIdeaPluginIntegrationTest {
    @Override
    PlayApp getPlayApp() {
        new AdvancedPlayApp()
    }

    String[] getSourcePaths() {
        [ "public", "conf", "app",
          "templates", "app/assets",
          "build/src/play/binary/javaTwirlTemplatesScalaSources", "build/src/play/binary/minifyPlayBinaryPlayBinaryCoffeeScriptJavaScript", "build/src/play/binary/minifyPlayBinaryPlayJavaScript", "build/src/play/binary/coffeeScriptJavaScript",
          "build/src/play/binary/routesScalaSources", "build/src/play/binary/twirlTemplatesScalaSources" ]
    }

    String[] getBuildTasks() {
        [ ":compilePlayBinaryPlayCoffeeScript", ":compilePlayBinaryPlayJavaTwirlTemplates", ":compilePlayBinaryPlayRoutes", ":compilePlayBinaryPlayTwirlTemplates", ":ideaProject", ":minifyPlayBinaryPlayBinaryCoffeeScriptJavaScript", ":minifyPlayBinaryPlayJavaScript", ":ideaModule", ":ideaWorkspace", ":idea" ]
    }

    int getExpectedScalaClasspathSize() {
        116
    }
}
