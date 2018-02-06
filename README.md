# Virtualiseringsgeneratorn
---

## To run

<pre><code>
groovy &lt;path_to_app&gt;\VirtualiseringGenerator.groovy --sourceDir &lt;path_to_resources&gt;\ServiceContracts_crm_scheduling_1.1.2\schemas --servicedomain &lt;servicedomain_version&gt;
</code></pre>
                               
### Input Parameters


|  Short | Long | Desc. |
|----------	|-----------------------|-----------------------------	|
| -d &lt;arg&gt; | --sourceDir &lt;arg&gt; 	| path to directory with jars 	|
| -s &lt;arg&gt; | --servicedomain &lt;arg&gt;  | version for the service domain e.g <i>2.1.0-RC2</i> |
| -n &lt;arg&gt; | --shortname &lt;arg&gt; | (optional) If it has a value no subdomain is added to the endpoint URL |
| -h &lt;arg&gt; | --help | Print help (More or less this text) |

<br />

This tool generates service virtualising components based on service
interactions found in &lt;sourceDir&gt;. <br />
They are generated in the directory where script is executed.<p />
Point --sourceDir to the schemas dir containing: [core_components] [interactions].<p />To be able to run this tool you need to have the
virtualServiceArchetype installed,found under <i>tools/virtualization/[trunk|tags]/virtualServiceArchetype/</i>
<br />

### Script Output:
New maven folders containing service interactions