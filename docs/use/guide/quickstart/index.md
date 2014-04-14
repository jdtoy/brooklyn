---
title: Getting Started
layout: page
toc: ../guide_toc.json
categories: [use, guide]
---

{% include fields.md %}

This guide will walk you through deploying an application to a public cloud, and managing that application.

We will be deploying an example 3-tier web application, described using this blueprint: 

{% highlight yaml %}
{% readj my-web-cluster.yaml %}
{% endhighlight %}

(This is written in YAML, following the [camp specification](https://www.oasis-open.org/committees/camp/). )


## Install Brooklyn

Download the [Brooklyn distribution]({{ this_dist_url_tgz }}) and expand it to your home directory ( `~/` ), or in a location of your choice. (Other [download options]({{site.url}}/start/download.html) are available.)

{% if site.brooklyn-version contains 'SNAPSHOT' %}
Expand the `tar.gz` archive (note: as this is a -SNAPSHOT version, your filename will be slightly different):
{% else %}
Expand the `tar.gz` archive:
{% endif %}

{% if site.brooklyn-version contains 'SNAPSHOT' %}
{% highlight bash %}
$ tar -zxf brooklyn-dist-{{ site.brooklyn-version }}-timestamp-dist.tar.gz
{% endhighlight %}
{% else %}
{% highlight bash %}
$ tar -zxf brooklyn-dist-{{ site.brooklyn-version }}-dist.tar.gz
{% endhighlight %}
{% endif %}

This will create a `brooklyn-{{ site.brooklyn-version }}` folder.

Note: you'll also need Java JRE or SDK installed (version 6 or later).

## Launch Brooklyn

Let's setup some paths for easy commands.

{% highlight bash %}
$ cd brooklyn-{{ site.brooklyn-version }}
$ BROOKLYN_DIR="$(pwd)"
$ export PATH=$PATH:$BROOKLYN_DIR/bin/
{% endhighlight %}

We can do a quick test drive by launching Brooklyn:

{% highlight bash %}
$ brooklyn launch
{% endhighlight %}

Brooklyn will output the address of the management interface:


`INFO  Starting brooklyn web-console on loopback interface because no security config is set`

`INFO  Started Brooklyn console at http://127.0.0.1:8081/, running classpath://brooklyn.war and []`

But before we really use Brooklyn, we need to setup some Locations.
 
Stop Brooklyn with ctrl-c.

## Configuring a Location

Brooklyn deploys applications to Locations. Locations can be clouds, machines with fixed IPs or localhost (for testing).

Brooklyn loads Location configuration  from `~/.brooklyn/brooklyn.properties`. 

Create a `.brooklyn` folder in your home directory:

{% highlight bash %}
$ mkdir ~/.brooklyn
{% endhighlight %}

Download the template [brooklyn.properties](brooklyn.properties)  and place this in `~/.brooklyn`.  

Open the file in a text editor and add your cloud credentials. If you would rather test Brooklyn on localhost, follow [these instructions]({{site.url}}/use/guide/locations/) to ensure that your Brooklyn can access your machine.

Restart Brooklyn:

{% highlight bash %}
$ brooklyn launch
{% endhighlight %}

## Launching an Application

There are several ways to deploy a YAML blueprint (including specifying it on the command line and submitting it via the REST API).

For now we will simply copy-and-paste the raw YAML blueprint into the web console.

When opening the web console for the first time ([127.0.0.1:8081](http://127.0.0.1:8081)) the Add Application dialog is displayed.
Select the YAML tab.

![Brooklyn web console, showing the YAML tab of the Add Application dialog.](add-application-modal-yaml.png)


### Chose your Cloud / Location

Edit the 'location' parameter in the YAML template (repeated below) to use the location you configured.

Replace:
{% highlight yaml %}
location: location
{% endhighlight %}

with (for example, one of):
{% highlight yaml %}
location: localhost
location: google-compute-engine:europe-west1-a
location: aws-ec2:us-east-1
location: rackspace-cloudservers-us:ORD
{% endhighlight %}

** My Web Cluster Template **

{% highlight yaml %}
{% readj my-web-cluster.yaml %}
{% endhighlight %}


Click "Finish". You should see your application listed, with status "STARTING".

![My Web Cluster is STARTING.](my-web-cluster-starting.png)


## Monitoring and Managing Applications

In the Brooklyn web-console clicking on an application listed on the home page, or the Applications tab, will show all the applications currently running.

We can explore the management hierarchy of an application, which will show us the entities it is composed of. If you have deployed the above YAML, then you'll see a standard 3-tier web-app. Clicking on the ControlledDynamicWebAppCluster will show if the cluster is ready to serve and, when ready, will provide a web address for the front of the loadbalancer.

If the service.isUp, you can view the demo web application in your browser at the webapp.url.

Through the Activities tab, you can drill into the activities each entity is doing or has recently done. Click on the task to see its details, and to drill into its "Children Tasks". For example, if you drill into MySqlNode's start operation, you can see the "Start (processes)", then "launch", and then the ssh command used including the stdin, stdout and stderr.


## Stopping the application

To stop an application, select the application in the tree view, click on the Effectors tab, and invoke the "Stop" effector. This will cleanly shutdown all components in the application.

### Testing the Policies

Brooklyn at its heart is a policy driven management plane which can implement business and technical policies.

The Web Cluster with DB demo comes pre-configured with an `AutoScalerPolicy`, attached to
the cluster of JBoss7 servers and a `targets` policy attached to the loadbalancer. You can
 observe policies this in the management console using the Policy tab of the relevant
 entity (e.g. `DynamicWebAppCluster` shows the `AutoScalerPolicy`.

The cluster autoscaler policy will automatically scale the cluster up or down to be the
right size for the current load. ('One server' is the minimum size allowed by the policy.)
The loadbalancer will automatically be updated by the targets policy as the cluster size
changes.

Sitting idle, your cluster will only contain one server, but you can check that the policy
works  using a tool like [jmeter](http://jmeter.apache.org/) pointed at the nginx endpoint
to create load on the cluster.

### Next 

The [Elastic Web Cluster Example]({{site.url}}/use/examples/webcluster/index.html) page 
details how to build the demo application from scratch. It shows how Brooklyn can 
complement your application with policy driven management, and how an application can be 
run without using the service catalog.
