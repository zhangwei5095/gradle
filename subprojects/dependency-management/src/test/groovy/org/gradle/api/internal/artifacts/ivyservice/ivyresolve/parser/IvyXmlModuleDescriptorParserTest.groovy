/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.SetMultimap
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.IvyDependencyMetadata
import org.gradle.internal.component.external.model.MutableIvyModuleResolveMetadata
import org.gradle.internal.resource.local.DefaultLocallyAvailableExternalResource
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Resources
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.api.internal.component.ArtifactType.IVY_DESCRIPTOR

class IvyXmlModuleDescriptorParserTest extends Specification {
    @Rule
    public final Resources resources = new Resources()
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    IvyXmlModuleDescriptorParser parser = new IvyXmlModuleDescriptorParser()
    DescriptorParseContext parseContext = Mock()

    ModuleDescriptorState md
    MutableIvyModuleResolveMetadata metadata

    def "parses minimal Ivy descriptor"() {
        when:
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          revision="myrev"
    />
</ivy-module>
"""
        parse(parseContext, file)

        then:
        md != null
        md.componentIdentifier == componentId("myorg", "mymodule", "myrev")
        md.status == "integration"
        metadata.configurationDefinitions.keySet() == ["default"] as Set
        metadata.dependencies.empty

        artifact()
    }

    def "adds implicit configurations"() {
        when:
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          revision="myrev"
          status="integration"
          publication="20041101110000"
    />
    <dependencies>
    </dependencies>
</ivy-module>
"""
        parse(parseContext, file)

        then:
        md != null
        md.componentIdentifier == componentId("myorg", "mymodule", "myrev")
        md.status == "integration"
        metadata.configurationDefinitions.keySet() == ["default"] as Set
        metadata.dependencies.empty

        def artifact = artifact()
        artifact.artifactName.name == "mymodule"
        artifact.artifactName.type == "jar"
        artifact.configurations == ["default"] as Set
    }

    def "adds implicit artifact when none declared"() {
        when:
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          revision="myrev"
    />
    <configurations>
        <conf name="A"/>
        <conf name="B"/>
    </configurations>
</ivy-module>
"""
        parse(parseContext, file)

        then:
        md != null
        md.componentIdentifier == componentId("myorg", "mymodule", "myrev")
        md.status == "integration"
        metadata.configurationDefinitions.keySet() == ["A", "B"] as Set
        metadata.dependencies.empty

        def artifact = artifact()
        artifact.artifactName.name == 'mymodule'
        artifact.artifactName.type == 'jar'
        artifact.artifactName.extension == 'jar'
        artifact.configurations == ["A", "B"] as Set
    }

    def "fails when ivy.xml uses unknown version of descriptor format"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="unknown">
    <info organisation="myorg"
          module="mymodule"
    />
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message == "invalid version unknown"
    }

    def "fails when configuration extends an unknown configuration"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          status="integration"
    />
    <configurations>
        <conf name="A" extends="invalidConf"/>
    </configurations>
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message == "Configuration 'A' extends configuration 'invalidConf' which is not declared."
    }

    def "fails when artifact is mapped to an unknown configuration"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          status="integration"
    />
    <configurations>
        <conf name="A"/>
    </configurations>
    <publications>
        <artifact conf="A,unknown"/>
    </publications>
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message == "Artifact mymodule.jar is mapped to configuration 'unknown' which is not declared."
    }

    def "fails when exclude is mapped to an unknown configuration"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          status="integration"
    />
    <configurations>
        <conf name="A"/>
    </configurations>
    <dependencies>
        <exclude org="other" conf="A,unknown"/>
    </dependencies>
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message == "Exclude rule other#*!*.* is mapped to configuration 'unknown' which is not declared."
    }

    def "fails when dependency is mapped from an unknown configuration"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          status="integration"
    />
    <configurations>
        <conf name="A"/>
    </configurations>
    <dependencies>
        <dependency name="other" rev="1.2" conf="A,unknown->%"/>
    </dependencies>
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message.contains("Cannot add dependency 'myorg#other;1.2' to configuration 'unknown'")
    }

    def "fails when there is a cycle in configuration hierarchy"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="mymodule"
          status="integration"
    />
    <configurations>
        <conf name="A" extends="B"/>
        <conf name="B" extends="A"/>
    </configurations>
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message == "illegal cycle detected in configuration extension: A => B => A"
    }

    def "fails when descriptor contains badly formed XML"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message.contains('"info"')
    }

    def "fails when descriptor does not match schema"() {
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <not-an-ivy-file/>
</ivy-module>
"""

        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message.contains('unknown tag not-an-ivy-file')
    }

    def "fails when descriptor does not declare module version id"() {
        def file = temporaryFolder.file("ivy.xml") << xml
        when:
        parse(parseContext, file)

        then:
        def e = thrown(MetaDataParseException)
        e.message == "Could not parse Ivy file ${file}"
        e.cause.message.contains('null name not allowed')

        where:
        xml << [
            """<ivy-module version="1.0">
                <info>
            </ivy-module>"""
        ]
    }

    def "parses a full Ivy descriptor"() {
        def file = temporaryFolder.file("ivy.xml")
        file.text = resources.getResource("test-full.xml").text

        when:
        parse(parseContext, file)

        then:
        md != null
        md.componentIdentifier == componentId("myorg", "mymodule", "myrev")
        md.status == "integration"
        md.publicationDate == new GregorianCalendar(2004, 10, 1, 11, 0, 0).getTime()

        TextUtil.normaliseLineSeparators(md.getDescription()) ==
            "This module is <b>great</b> !<br/>\n\tYou can use it especially with myconf1 and myconf2, and myconf4 is not too bad too."

        md.extraInfo.size() == 1
        md.extraInfo.get(new NamespaceId("http://ant.apache.org/ivy/extra", "someExtra")) == "56576"

        metadata.configurationDefinitions.size() == 5
        assertConf("myconf1", "desc 1", true, new String[0])
        assertConf("myconf2", "desc 2", true, new String[0])
        assertConf("myconf3", "desc 3", false, new String[0])
        assertConf("myconf4", "desc 4", true, ["myconf1", "myconf2"].toArray(new String[2]))
        assertConf("myoldconf", "my old desc", true, new String[0])

        md.artifacts.size() == 4
        assertArtifacts("myconf1", ["myartifact1", "myartifact2", "myartifact3", "myartifact4"])
        assertArtifacts("myconf2", ["myartifact1", "myartifact3"])
        assertArtifacts("myconf3", ["myartifact1", "myartifact3", "myartifact4"])
        assertArtifacts("myconf4", ["myartifact1"])

        metadata.dependencies.size() == 13

        verifyFullDependencies(metadata.dependencies)

        def rules = md.excludes
        rules.size() == 2
        rules[0].matcher == PatternMatcher.GLOB
        rules[0].configurations as List == ["myconf1"]
        rules[1].matcher == PatternMatcher.EXACT
        rules[1].configurations as List == ["myconf1", "myconf2", "myconf3", "myconf4", "myoldconf"]
    }

    def "merges values from parent Ivy descriptor"() {
        given:
        def parentFile = temporaryFolder.file("parent.xml") << """
<ivy-module version="1.0">
    <info organisation="myorg"
          module="parent"
          revision="parentrev"
          status="not-the-default"
          publication="20041101110000"
    />
    <configurations>
        <conf name='default'/>
    </configurations>
    <dependencies>
        <dependency conf="*->*" org="deporg" name="depname" rev="deprev"/>
    </dependencies>
</ivy-module>
"""
        def file = temporaryFolder.file("ivy.xml") << """
<ivy-module version="1.0">
    <info module="mymodule" revision="myrev">
        <extends organisation="myorg" module="parent" revision="parentrev"/>
    </info>
</ivy-module>
"""
        and:
        parseContext.getMetaDataArtifact(_, IVY_DESCRIPTOR) >> new DefaultLocallyAvailableExternalResource(parentFile.toURI(), new DefaultLocallyAvailableResource(parentFile))

        when:
        parse(parseContext, file)

        then:
        md != null
        md.componentIdentifier == componentId("myorg", "mymodule", "myrev")
        md.status == "integration"
        metadata.configurationDefinitions.keySet() == ["default"] as Set

        metadata.dependencies.size() == 1
        def dependency = metadata.dependencies.first()
        dependency.requested == newSelector("deporg", "depname", "deprev")
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2766")
    def "defaultconfmapping is respected"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev"
                      status="integration"
                      publication="20041101110000">
                </info>
                <configurations>
                    <conf name="myconf" />
                </configurations>
                <publications/>
                <dependencies defaultconfmapping="myconf->default">
                    <dependency name="mymodule2" rev="1.2"/>
                </dependencies>
            </ivy-module>
        """

        when:
        parse(parseContext, file)
        def dependency = metadata.dependencies.first()

        then:
        dependency.confMappings.keySet() == ["myconf"] as Set
    }

    def "defaultconf is respected"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev"
                      status="integration"
                      publication="20041101110000">
                </info>
                <configurations>
                    <conf name="conf1" />
                    <conf name="conf2" />
                </configurations>
                <publications defaultconf="conf2">
                    <artifact/>
                </publications>
            </ivy-module>
        """

        when:
        parse(parseContext, file)

        then:
        artifact().configurations == ["conf2"] as Set
    }

    def "parses dependency config mappings"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev">
                </info>
                <configurations>
                    <conf name="a" />
                    <conf name="b" />
                    <conf name="c" />
                </configurations>
                <publications/>
                <dependencies>
                    <dependency name="mymodule2" rev="1.2" conf="a"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->other"/>
                    <dependency name="mymodule2" rev="1.2" conf="*->@"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->other;%->@"/>
                    <dependency name="mymodule2" rev="1.2" conf="*,!a->@"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->*"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->one,two;a,b->three;*->four;%->none"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->#"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->a;%->@"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->a;*,!a->b"/>
                    <dependency name="mymodule2" rev="1.2" conf="*->*"/>
                    <dependency name="mymodule2" rev="1.2" conf=""/>
                    <dependency name="mymodule2" rev="1.2"/>
                </dependencies>
            </ivy-module>
        """

        when:
        parse(parseContext, file)

        then:
        metadata.dependencies[0].confMappings == map("a": ["a"])
        metadata.dependencies[1].confMappings == map("a": ["other"])
        metadata.dependencies[2].confMappings == map("*": ["@"])
        metadata.dependencies[3].confMappings == map("a": ["other"], "%": ["@"])
        metadata.dependencies[4].confMappings == map("*": ["@"], "!a": ["@"])
        metadata.dependencies[5].confMappings == map("a": ["*"])
        metadata.dependencies[6].confMappings == map("a": ["one", "two", "three"], "b": ["three"], "*": ["four"], "%": ["none"])
        metadata.dependencies[7].confMappings == map("a": ["#"])
        metadata.dependencies[8].confMappings == map("a": ["a"], "%": ["@"])
        metadata.dependencies[9].confMappings == map("a": ["a"], "*": ["b"], "!a": ["b"])
        metadata.dependencies[10].confMappings == map("*": ["*"])
        metadata.dependencies[11].confMappings == map("*": ["*"])
        metadata.dependencies[12].confMappings == map("*": ["*"])
    }

    def "parses dependency config mappings with defaults"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev">
                </info>
                <configurations>
                    <conf name="a" />
                    <conf name="b" />
                    <conf name="c" />
                </configurations>
                <publications/>
                <dependencies defaultconf="a" defaultconfmapping="a->a1;b->b1,b2">
                    <dependency name="mymodule2" rev="1.2"/>
                    <dependency name="mymodule2" rev="1.2" conf=""/>
                    <dependency name="mymodule2" rev="1.2" conf="a"/>
                    <dependency name="mymodule2" rev="1.2" conf="b"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->other"/>
                    <dependency name="mymodule2" rev="1.2" conf="*->@"/>
                    <dependency name="mymodule2" rev="1.2" conf="c->other"/>
                    <dependency name="mymodule2" rev="1.2" conf="a->"/>
                </dependencies>
            </ivy-module>
        """

        when:
        parse(parseContext, file)

        then:
        metadata.dependencies[0].confMappings == map("a": ["a1"])
        metadata.dependencies[1].confMappings == map("a": ["a1"])
        metadata.dependencies[2].confMappings == map("a": ["a1"])
        metadata.dependencies[3].confMappings == map("b": ["b1", "b2"])
        metadata.dependencies[4].confMappings == map("a": ["other"])
        metadata.dependencies[5].confMappings == map("*": ["@"])
        metadata.dependencies[6].confMappings == map("c": ["other"])
        metadata.dependencies[7].confMappings == map("a": ["a1"])

    }

    def "parses artifact config mappings"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev">
                </info>
                <configurations>
                    <conf name="a" />
                    <conf name="b" />
                    <conf name="c" extends="a"/>
                    <conf name="d" />
                </configurations>
                <publications>
                    <artifact/>
                    <artifact name='art2' type='type' ext='ext' conf='*'/>
                    <artifact name='art3' type='type2' conf='a, b  '/>
                </publications>
            </ivy-module>
        """

        when:
        parse(parseContext, file)

        then:
        assertArtifacts("a", ["mymodule", "art2", "art3"])
        assertArtifacts("b", ["mymodule", "art2", "art3"])
        assertArtifacts("c", ["mymodule", "art2"])
        assertArtifacts("d", ["mymodule", "art2"])

        and:
        artifacts("a")*.artifactName*.name == ['mymodule', 'art2', 'art3']
        artifacts("a")*.artifactName*.type == ['jar', 'type', 'type2']
        artifacts("a")*.artifactName*.extension == ['jar', 'ext', 'type2']

        and:
        artifacts("b")*.artifactName*.name == ['mymodule', 'art2', 'art3']
        artifacts("c")*.artifactName*.name == ['mymodule', 'art2']
        artifacts("d")*.artifactName*.name == ['mymodule', 'art2']
    }

    def "accumulates configurations if the same artifact listed more than once"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0">
                <info organisation="myorg" module="mymodule" revision="myrev"/>
                <configurations><conf name="a"/><conf name="b"/><conf name="c"/><conf name="d"/><conf name="e"/></configurations>
                <publications>
                    <artifact name='art' type='type' ext='ext' conf='a,b'/>
                    <artifact name='art' type='type' ext='ext' conf='b,c'/>
                </publications>
            </ivy-module>
        """

        when:
        parse(parseContext, file)

        then:
        artifact().configurations == ['a', 'b', 'c'] as Set
    }

    def "parses extra info"() {
        given:
        def file = temporaryFolder.createFile("ivy.xml")
        file.text = """
           <ivy-module version="2.0" xmlns:b="namespace-b" xmlns:c="namespace-c">
                <info organisation="myorg"
                      module="mymodule"
                      revision="myrev"
                      b:a="1"
                      b:b="2"
                      c:a="3">
                    <b:a>info 1</b:a>
                    <c:a>info 2</c:a>
                </info>
            </ivy-module>
        """

        when:
        parse(parseContext, file)

        then:
        md.componentIdentifier == componentId("myorg", "mymodule", "myrev")
        md.extraInfo.size() == 2
        md.extraInfo[new NamespaceId("namespace-b", "a")] == "info 1"
        md.extraInfo[new NamespaceId("namespace-c", "a")] == "info 2"
    }

    private void parse(DescriptorParseContext parseContext, TestFile file) {
        metadata = parser.parseMetaData(parseContext, file)
        md = metadata.descriptor
    }

    private artifact() {
        assert md.artifacts.size() == 1
        md.artifacts[0]
    }

    private artifacts(String conf) {
        md.artifacts.findAll { it.configurations.contains(conf) }
    }

    static componentId(String group, String module, String version) {
        DefaultModuleComponentIdentifier.newId(group, module, version)
    }

    def verifyFullDependencies(Collection<IvyDependencyMetadata> dependencies) {
        // no conf def => equivalent to *->*
        def dd = getDependency(dependencies, "mymodule2")
        assert dd.requested == newSelector("myorg", "mymodule2", "2.0")
        assert dd.confMappings == map("*": ["*"])
        assert !dd.changing
        assert dd.transitive
        assert dd.dependencyArtifacts.empty

        // changing, not transitive
        dd = getDependency(dependencies, "mymodule3")
        assert dd.changing
        assert !dd.transitive

        // conf="myconf1" => equivalent to myconf1->myconf1
        dd = getDependency(dependencies, "yourmodule1")
        assert dd.requested == newSelector("yourorg", "yourmodule1", "1.1")
        assert dd.dynamicConstraintVersion == "1+"
        assert dd.confMappings == map(myconf1: ["myconf1"])
        assert dd.dependencyArtifacts.empty

        // conf="myconf1->yourconf1"
        dd = getDependency(dependencies, "yourmodule2")
        assert dd.requested == newSelector("yourorg", "yourmodule2", "2+")
        assert dd.confMappings == map(myconf1: ["yourconf1"])
        assert dd.dependencyArtifacts.empty

        // conf="myconf1->yourconf1, yourconf2"
        dd = getDependency(dependencies, "yourmodule3")
        assert dd.requested == newSelector("yourorg", "yourmodule3", "3.1")
        assert dd.confMappings == map(myconf1: ["yourconf1", "yourconf2"])
        assert dd.dependencyArtifacts.empty

        // conf="myconf1, myconf2->yourconf1, yourconf2"
        dd = getDependency(dependencies, "yourmodule4")
        assert dd.requested == newSelector("yourorg", "yourmodule4", "4.1")
        assert dd.confMappings == map(myconf1:["yourconf1", "yourconf2"], myconf2:["yourconf1", "yourconf2"])
        assert dd.dependencyArtifacts.empty

        // conf="myconf1->yourconf1 | myconf2->yourconf1, yourconf2"
        dd = getDependency(dependencies, "yourmodule5")
        assert dd.requested == newSelector("yourorg", "yourmodule5", "5.1")
        assert dd.confMappings == map(myconf1:["yourconf1"], myconf2:["yourconf1", "yourconf2"])
        assert dd.dependencyArtifacts.empty

        // conf="*->@"
        dd = getDependency(dependencies, "yourmodule11")
        assert dd.requested == newSelector("yourorg", "yourmodule11", "11.1")
        assert dd.confMappings == map("*":["@"])
        assert dd.dependencyArtifacts.empty

        // Conf mappings as nested elements
        dd = getDependency(dependencies, "yourmodule6")
        assert dd.requested == newSelector("yourorg", "yourmodule6", "latest.integration")
        assert dd.confMappings == map(myconf1:["yourconf1"], myconf2:["yourconf1", "yourconf2"])
        assert dd.dependencyArtifacts.empty

        // Conf mappings as deeply nested elements
        dd = getDependency(dependencies, "yourmodule7")
        assert dd.requested == newSelector("yourorg", "yourmodule7", "7.1")
        assert dd.confMappings == map(myconf1:["yourconf1"], myconf2:["yourconf1", "yourconf2"])
        assert dd.dependencyArtifacts.empty

        // Dependency artifacts
        dd = getDependency(dependencies, "yourmodule8")
        assert dd.requested == newSelector("yourorg", "yourmodule8", "8.1")
        assert dd.dependencyArtifacts.size() == 2
        assertDependencyArtifact(dd, "yourartifact8-1", ["myconf1", "myconf2", "myconf3", "myconf4", "myoldconf"])
        assertDependencyArtifact(dd, "yourartifact8-2", ["myconf1", "myconf2", "myconf3", "myconf4", "myoldconf"])

        // Dependency artifacts with confs
        dd = getDependency(dependencies, "yourmodule9")
        assert dd.requested == newSelector("yourorg", "yourmodule9", "9.1")
        assert dd.dependencyArtifacts.size() == 2
        assertDependencyArtifact(dd, "yourartifact9-1", ["myconf1", "myconf2"])
        assertDependencyArtifact(dd, "yourartifact9-2", ["myconf2", "myconf3"])

        // Dependency excludes
        dd = getDependency(dependencies, "yourmodule10")
        assert dd.requested == newSelector("yourorg", "yourmodule10", "10.1")
        assert dd.dependencyArtifacts.empty
        assert dd.dependencyExcludes.size() == 1
        assert dd.dependencyExcludes[0].artifact.name == "toexclude"
        assert dd.dependencyExcludes[0].configurations as Set == ["myconf1", "myconf2", "myconf3", "myconf4", "myoldconf"] as Set
        true
    }

    protected SetMultimap<String, String> map(Map<String, List<String>> map) {
        SetMultimap<String, String> result = LinkedHashMultimap.create()
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            result.putAll(entry.key, entry.value)
        }
        return result
    }

    protected void assertArtifacts(String configuration, List<String> artifactNames) {
        def configurationArtifactNames = artifacts(configuration)*.artifactName*.name
        assert configurationArtifactNames as Set == artifactNames as Set
    }

    protected void assertConf(String name, String desc, boolean visible, String[] exts) {
        def conf = metadata.configurationDefinitions[name]
        assert conf != null : "configuration not found: " + name
        assert conf.name == name
        assert conf.visible == visible
        assert conf.extendsFrom as Set == exts as Set
    }

    protected static IvyDependencyMetadata getDependency(Collection<IvyDependencyMetadata> dependencies, String name) {
        def found = dependencies.find { it.requested.name == name }
        assert found != null
        return found
    }

    protected static void assertDependencyArtifact(IvyDependencyMetadata dd, String name, List<String> confs) {
        def artifact = dd.dependencyArtifacts.find { it.artifactName.name == name }
        assert artifact != null
        assert artifact.configurations == confs as Set
    }
}
