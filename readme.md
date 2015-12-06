# Rocannon a Playbook Server

This is a simple http server that runs ansible-playbooks
It is based on the Play framework.

## Running
Run as a normal Play app or as a docker image:

> sbt run

or

> sbt start

### Docker instructions

> sbt docker:publishLocal
>
> docker run -d -v [path-to-playbooks]:/playbooks rocannon

## Accessing

Use http POST to http://rocannon/inventory-file/playbook-file

Where inventory-file and playbook-file may be relative to the playbook directory.

The data is json encoded extra-vars:

<code>
{ 
    "variable1" : "value",
    "variable2" : "value"
}
</code>

Make sure to use <code>Content-Type:application/json</code>.

## Configuration
The following environment variables can be set
  
<table>
    <tr><th>Name</th><th>Description</th><th>Default</th></tr>
    <tr>
        <td>ANSIBLE_COMMAND</td>
        <td>Path to the ansible-playbook command</td>
        <td>/usr/local/bin/ansible-playbook</td>
        </tr>
    <tr>
        <td>PLAYBOOKS</td>
        <td>Path to the playbooks</td>
        <td>/playbooks</td>
    </tr>
    <tr>
        <td>VAULT_PASSWORD_FILE</td>
        <td>Path to the vault-password-file</td>
        <td>/root/.vault-password</td>
    </tr> 
    <tr>
        <td>CRYPTO_SECRET</td>
        <td>The play crypto secret to use</td>
        <td>Should be set.</td>
    </tr>
</table>


