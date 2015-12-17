# Rocannon an Ansible Playbook Server

This is a simple http server that runs ansible-playbooks
It is written in Scala and based on the Play Framework.

## Running
Run as a Play app or as a docker image:

    > sbt run

or

    > sbt start</code>

### Docker instructions
The build.sbt uses GitVersioning, so you must set a version git tag in your repo.

    > git tag mytag-0.1.0
    > sbt docker:publishLocal
    > docker run -d -v [path-to-playbooks]:/playbooks rocannon:mytag-0.1.0

## Accessing

### Http 
Use http POST to `http://rocannon/inventory-file/playbook-file[?refId=refId]`

Where inventory-file and playbook-file may be relative to the playbook directory.

The data is json encoded extra-vars:

    {
        "variable1" : "value",
        "variable2" : "value"
    }

Make sure to use `Content-Type:application/json`.

The optional `refId` query parameter can be any string. And will be used in logs and the reply.

### Script

The bin/rocannon bash script can be used for convenience. 

### Response
Json formatted, example:
    
    {"buildId":606186755,"refId":"myId","status":"success","execTime":17,"message":"\nPLAY... trucated"} 

## Configuration

### Config file
Uses TypeSafes config. Use a -Dconfig.file switch to override. See TypeSafe/Play Framework for more info.

### Environment variables
The following environment variables can be set
  
<table>
    <tr><th>Name</th><th>Description</th><th>Default</th></tr>
    <tr>
        <td>ANSIBLE_COMMAND</td>
        <td>Path to the ansible-playbook command</td>
        <td>/usr/bin/ansible-playbook</td>
        </tr>
    <tr>
        <td>PLAYBOOKS</td>
        <td>Path to the playbooks</td>
        <td>/playbooks</td>
    </tr>
    <tr>
        <td>VAULT_PASSWORD</td>
        <td>The vault password to use</td>
        <td>changeme</td>
    </tr> 
    <tr>
        <td>CRYPTO_SECRET</td>
        <td>The play crypto secret to use</td>
        <td>Should be set.</td>
    </tr>
        <tr>
        <td>LE_TOKEN</td>
        <td>Logentries token</td>
        <td>Will send logs to L``ogentries</td>
    </tr>
</table>


