package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;

public class Branch implements Serializable {
    private HashMap<String,String> branch;

    public Branch() {
        branch = new HashMap<>();
    }
    public HashMap<String, String> getBranch() {
        return branch;
    }

    public static Branch load() {
        File file = Utils.join(Repository.GITLET_DIR,Repository.BRANCH_FILE);
        Branch branch = Utils.readObject(file,Branch.class);
        return branch;
    }

    public void save() {
        File file = Utils.join(Repository.GITLET_DIR, Repository.BRANCH_FILE);
        Utils.writeObject(file,this);
    }
}
