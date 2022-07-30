package gitlet;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

import static gitlet.Utils.*;


/**
 * Represents a gitlet repository.
 * implements init, add, rm, commit, log, status, find, checkout, branch, merge commands.
 *
 * @author Daniel Michles
 */
public class Repository {

    /**
     * The current working directory.
     */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /**
     * The .gitlet directory.
     */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /**
     * The .gitlet/stagingArea directory.
     */
    public static final File STAGING_DIR = join(GITLET_DIR, "stagingarea");
    /**
     * The .gitlet/stagingforremoval directory.
     */
    public static final File STAGING_FOR_REMOVAL_DIR = join(GITLET_DIR, "stagingforremoval");
    /**
     * The .gitlet/commitedFiles directory.
     */
    public static final File COMMITTED_DIR = join(GITLET_DIR, "committedfiles");
    /**
     * The .gitlet/commits directory.
     */
    public static final File COMMITS_DIR = join(GITLET_DIR, "commits");

    public static final String BRANCH_FILE = "branch";

    public static final String HEAD_FILE = "head";
    /* TODO: fill in the rest of this class. */

    public static void initCommand() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            return;
        }
        GITLET_DIR.mkdir();
        STAGING_DIR.mkdir();
        COMMITS_DIR.mkdir();
        COMMITTED_DIR.mkdir();
        STAGING_FOR_REMOVAL_DIR.mkdir();

        /* Create initial commit and hash it */
        Commit initCommit = new Commit();
        String hashName = initCommit.sha1();
        /* Serialize the initial commit */
        initCommit.save();
        /* Create reference objects to track branch and HEAD */

        Head head = new Head();
        head.setBranch("master");
        head.setCommitReference(hashName);
        Branch branch = new Branch();
        branch.getBranch().put("master", hashName);

        /* Serialize branch and head */
        branch.save();
        head.save();
    }

    public static void add(String fileName) {
        /* Check if file fileName exists */
        File file = join(CWD, fileName);
        if (!file.exists()) {
            System.out.println(String.format("File %s does not exit", fileName));
            return;
        }

        /* The file will no longer be staged for removal (see gitlet rm), if it was at the time of the add command. */
        List<String> list = plainFilenamesIn(STAGING_FOR_REMOVAL_DIR);
        if (list != null && list.contains(fileName)) {
            File f = join(STAGING_FOR_REMOVAL_DIR, fileName);
            f.delete();
        }

        /* Retrieve head object containing references to last commit */
        Head head = Head.load();
        //TO DO check if number of commits is greater than 2, this still works, just performs unnecessary checks
        /* Retrieve last commit from reference file */
        Commit commit = Commit.load(head.getCommitReference());
        /* Check if last commit contains fileName */
        if (commit.getMap().containsKey(fileName)) {
            File file1 = join(CWD, fileName);
            String fileHash = sha1(readContentsAsString(file1));
            /* Check if hashes are the same */
            if (fileHash.equals(commit.getMap().get(fileName))) {
                File file2 = join(STAGING_DIR, fileName);
                /* Delete filename file if it exists in the staging area */
                if (file2.exists()) {
                    file2.delete();
                }
                return;
            }
        }

        /* Copy fileName to Staging Area  */
        File stagedFile = join(STAGING_DIR, fileName);
        copyFile(file, stagedFile);
    }

    public static void commit(String message) {
        if (Utils.plainFilenamesIn(STAGING_DIR).isEmpty() && Utils.plainFilenamesIn(STAGING_FOR_REMOVAL_DIR).isEmpty()) {
            System.out.println("No changes added to the commit.");
            return;
        }
        /* Retrieve head object containing references to last commit */
        Head head = Head.load();
        Branch branch = Branch.load();
        /* Retrieve last commit from reference file */
        Commit commit = Commit.load(head.getCommitReference());
        /* Populate message, timestamp and parent reference fields */
        commit.setMessage(message);
        commit.setTimeStamp(new Date());
        commit.setParentReference(head.getCommitReference());

        /* Go through staging areas and do appropriate operations */
        commit = commitFileOps(commit);

        /* Hash the commit and update pointers */
        String hashName = sha1(commit.toString());
        head.setCommitReference(hashName);
        branch.getBranch().put(head.getBranch(), hashName);

        /* Serialize pointer objects */
        branch.save();
        head.save();

        /* Serialize the commit object */
        commit.save();
    }

    public static Commit commitFileOps(Commit commit) {
        /* Populate staged file references in commit */
        List<String> stagedFiles = Utils.plainFilenamesIn(STAGING_DIR);
        if (!stagedFiles.isEmpty()) {
            for (String file : stagedFiles) {
                File f = join(STAGING_DIR, file);
                String hashedFile = sha1(readContentsAsString(f));
                commit.getMap().put(file, hashedFile);
                File committed = join(COMMITTED_DIR, hashedFile);
                copyFile(f, committed);
                f.delete();
            }
        }

        /* Go through the staged for removal and remove from the current commit */
        List<String> stagedForRemovalFiles = Utils.plainFilenamesIn(STAGING_FOR_REMOVAL_DIR);
        if (!stagedForRemovalFiles.isEmpty()) {
            stagedForRemovalFiles.forEach(file -> {
                commit.getMap().remove(file);
                File f = join(STAGING_FOR_REMOVAL_DIR, file);
                f.delete();
            });
        }
        return commit;
    }

    public static void rm(String filename) {
        Head head = Head.load();
        String lastCommitRef = head.getCommitReference();
        Commit commit = Commit.load(lastCommitRef);

        /* If the file is neither staged nor tracked by the head commit, print the error message  */
        boolean staged = false;
        if (plainFilenamesIn(STAGING_DIR) != null) {
            List<String> filesStagedForRemoval = plainFilenamesIn(STAGING_DIR);
            if (filesStagedForRemoval.contains(filename)) {
                staged = true;
            }
        }
        if (!commit.getMap().containsKey(filename) && !staged) {
            System.out.println("No reason to remove the file.");
            return;
        }
        /* Unstage the file if it is currently staged for addition. */
        if (staged) {
            File f = join(STAGING_DIR, filename);
            f.delete();
            System.out.println(filename + " unstaged from addition");
        }
        /*If the file is tracked in the current commit, stage it for removal and remove the file
         from the working directory if the user has not already done so (do not remove it unless
         it is tracked in the current commit).*/
        if (commit.getMap().containsKey(filename)) {
            File f = join(CWD, filename);
            File f1 = join(STAGING_FOR_REMOVAL_DIR, filename);
            copyFile(f, f1);
            f.delete();
            System.out.println(filename + " staged for removal");
        }
    }

    public static void log() {
        Head head = Head.load();
        //TODO Starts from commit referenced by HEAD pointer on the current branch
        String commitRef = head.getCommitReference();
        while (commitRef != null) {
            Commit commit = Commit.load(commitRef);
            System.out.println("===");
            System.out.println(String.format("commit %s", commitRef));
            if (commit.getParentReference2() != null) {
                String parent1 = commit.getParentReference().substring(0, 7);
                String parent2 = commit.getParentReference2().substring(0, 7);
                System.out.println(String.format("Merge: %s %s", parent1, parent2));
            }

            Date date = commit.getTimeStamp();
            SimpleDateFormat df = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z");
            System.out.println(String.format("Date: %s", df.format(date)));
            String message = commit.getMessage();
            System.out.println(message);
            System.out.println();
            commitRef = commit.getParentReference();
            //TODO merged commits: The first parent is the branch you were on when you did the merge;
            // the second is that of the merged-in branch. DONE
        }
    }

    public static void globallog() {
        //TODO
    }

    public static void find(String message) {
        //TODO
        boolean flag = false;
        List<String> list = plainFilenamesIn(COMMITS_DIR);
        for (String c : list) {
            Commit commit = Commit.load(c);
            if (commit.getMessage().equals(message)) {
                System.out.println(c);
                flag = true;
            }
        }
        if (!flag) {
            System.out.println("Found no commit with that message.");
        }
    }

    public static void status() {
        Head head = Head.load();
        String br = head.getBranch();
        Branch branch = Branch.load();

        branches(branch, br);
        stagedForAddition();
        stagedForRemoval();
        modifiedNotStaged(head);
        untracked(head);

    }

    public static void branches(Branch branch, String headBranch) {
        System.out.println("=== Branches ===");
        List<String> branches = new ArrayList<>();
        for (HashMap.Entry<String, String> entry : branch.getBranch().entrySet()) {
            branches.add(entry.getKey());

        }
        Collections.sort(branches);
        for (String brnch : branches) {
            String s = headBranch.equals(brnch) ? "*" : "";
            System.out.println(s + brnch);
        }
        System.out.println();
    }

    public static void stagedForAddition() {
        System.out.println("=== Staged Files ===");
        List<String> stagedForAddition = plainFilenamesIn(STAGING_DIR);
        Collections.sort(stagedForAddition);
        for (String file : stagedForAddition) {
            System.out.println(file);
        }
        System.out.println();
    }

    public static void stagedForRemoval() {
        System.out.println("=== Removed Files ===");
        List<String> stagedForRemoval = plainFilenamesIn(STAGING_FOR_REMOVAL_DIR);
        Collections.sort(stagedForRemoval);
        for (String file : stagedForRemoval) {
            System.out.println(file);
        }
        System.out.println();
    }

    public static void modifiedNotStaged(Head head) {
        System.out.println("=== Modifications Not Staged For Commit ===");
        List<String> modNotStagedForCommit = new ArrayList<>();
        List<String> stagedForRemoval = plainFilenamesIn(STAGING_FOR_REMOVAL_DIR);
        List<String> stagedForAddition = plainFilenamesIn(STAGING_DIR);
        Commit commit = Commit.load(head.getCommitReference());

        List<String> committed = new ArrayList<>();
        for (HashMap.Entry<String, String> entry : commit.getMap().entrySet()) {
            committed.add(entry.getKey());
        }

        List<String> workingdirfiles = plainFilenamesIn(CWD);
        for (String file : workingdirfiles) {
            File f = join(CWD, file);
            String hashedFile = sha1(readContentsAsString(f));
            /* Tracked in the current commit, changed in the working directory, but not staged */
            if (commit.getMap().containsKey(file)) {
                if (!commit.getMap().get(file).equals(hashedFile) && !stagedForAddition.contains(file)) {
                    modNotStagedForCommit.add(file + " (modified)");
                }
            }
            /* Staged for addition, but with different contents than in the working directory */
            if (stagedForAddition.contains(file)) {
                File f1 = join(STAGING_DIR, file);
                String hashedf1 = sha1(readContentsAsString(f1));
                if (!hashedFile.equals(hashedf1)) {
                    modNotStagedForCommit.add(file + " (modified)");
                }
            }
        }

        /*Staged for addition, but deleted in the working directory */
        for (String file : stagedForAddition) {
            if (!workingdirfiles.contains(file)) {
                modNotStagedForCommit.add(file + " (deleted)");
            }
        }
        /*Not staged for removal, but tracked in the current commit and deleted from the working directory. */
        for (String file : committed) {
            if (!stagedForRemoval.contains(file) && !workingdirfiles.contains(file)) {
                modNotStagedForCommit.add(file + " (deleted)");
            }
        }

        for (String file : modNotStagedForCommit) {
            System.out.println(file);
        }
        System.out.println();
    }

    public static void untracked(Head head) {
        Commit commit = Commit.load(head.getCommitReference());
        List<String> untracked = getUntracked(commit);

        System.out.println("=== Untracked Files ===");
        for (String file : untracked) {
            System.out.println(file);
        }
        System.out.println();
    }


    /* Takes the version of the file as it exists in the head commit and puts it in the working directory,
    overwriting the version of the file that’s already there if there is one. The new version of the file is not staged.
     */
    public static void checkoutFile(String file) {
        File HEAD = join(GITLET_DIR, HEAD_FILE);
        Head head = readObject(HEAD, Head.class);
        String commitRef = head.getCommitReference();
        File com = join(COMMITS_DIR, commitRef);
        Commit commit = readObject(com, Commit.class);
        if (!commit.getMap().containsKey(file)) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        File target = join(CWD, file);
        File source = join(COMMITTED_DIR, commit.getMap().get(file));
        copyFile(source, target);
    }

    public static void checkoutCommit(String commitRef, String file) {
        List<String> listOfCommits = plainFilenamesIn(COMMITS_DIR);
        if (!listOfCommits.contains(commitRef)) {
            System.out.println("No commit with that id exists.");
            return;
        }
        File com = join(COMMITS_DIR, commitRef);
        Commit commit = readObject(com, Commit.class);
        if (!commit.getMap().containsKey(file)) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        File target = join(CWD, file);
        File source = join(COMMITTED_DIR, commit.getMap().get(file));
        copyFile(source, target);

    }

    public static void checkoutBranch(String branch) {
        File HEAD = join(GITLET_DIR, HEAD_FILE);
        Head head = readObject(HEAD, Head.class);

        String commitRef = head.getCommitReference();
        File com = join(COMMITS_DIR, commitRef);
        Commit commit = readObject(com, Commit.class);

        File BRANCH = join(GITLET_DIR, BRANCH_FILE);
        Branch br = readObject(BRANCH, Branch.class);

        /*If no branch with that name exists, print No such branch exists. */
        if (!br.getBranch().containsKey(branch)) {
            System.out.println("No such branch exists.");
            return;
        }
        /* If that branch is the current branch, print No need to checkout the current branch.. */
        if (head.getBranch().equals(branch)) {
            System.out.println("No need to checkout the current branch.");
            return;
        }
        /* If a working file is untracked in the current branch and would be overwritten by the checkout,
        print There is an untracked file in the way; delete it, or add and commit it first. and exit
         */
        List<String> committed = new ArrayList<>();
        for (HashMap.Entry<String, String> entry : commit.getMap().entrySet()) {
            committed.add(entry.getKey());
        }
        List<String> untracked = getUntracked(commit);

        if (!untracked.isEmpty()) {
            System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
            return;
        }
        /* Takes all files in the commit at the head of the given branch, and puts them in the working directory,
         overwriting the versions of the files that are already there if they exist. Also, at the end of this command,
          the given branch will now be considered the current branch (HEAD).
         */
        String newHeadCommit = br.getBranch().get(branch);
        File newCom = join(COMMITS_DIR, newHeadCommit);
        Commit newCommit = readObject(newCom, Commit.class);

        for (Map.Entry<String, String> entry : newCommit.getMap().entrySet()) {
            File source = join(COMMITTED_DIR, entry.getValue());
            File target = join(CWD, entry.getKey());
            copyFile(source, target);
        }
        head.setBranch(branch);
        head.setCommitReference(newHeadCommit);
        writeObject(HEAD, head);

        /* Any files that are tracked in the current branch but are not present in the checked-out branch are deleted.*/
        for (String file : committed) {
            if (!newCommit.getMap().containsKey(file)) {
                File f = join(CWD, file);
                if (f.exists()) {
                    f.delete();
                }
            }
        }
        /*  The staging areas are cleared */
        clearStagingArea();
    }

    public static void branch(String branch) {
        Head head = Head.load();
        Commit commit = Commit.load(head.getCommitReference());
        if (commit.getParentReference() == null) {
            System.out.println("Can't branch off initial commmit.");
            return;
        }
        Branch br = Branch.load();
        if (br.getBranch().containsKey(branch)) {
            System.out.println("A branch with that name already exists.");
            return;
        }
        br.getBranch().put(branch, head.getCommitReference());
        br.save();
    }

    /* Checks out all the files tracked by the given commit. Removes tracked files
     that are not present in that commit. Also moves the current branch’s head to that
     commit node. See the intro for an example of what happens to the head pointer after
     using reset. The [commit id] may be abbreviated as for checkout. The staging area is
      cleared. The command is essentially checkout of an arbitrary commit that also changes the current branch head.
     */
    public static void reset(String commitRef) {
        List<String> committs = plainFilenamesIn(COMMITS_DIR);
        if (!committs.contains(commitRef)) {
            System.out.println("No commit with that id exists.");
            return;
        }

        File HEAD = join(GITLET_DIR, HEAD_FILE);
        Head head = readObject(HEAD, Head.class);
        String headCommitRef = head.getCommitReference();
        File headCom = join(COMMITS_DIR, headCommitRef);
        Commit headCommit = readObject(headCom, Commit.class);
        File BRANCH = join(GITLET_DIR, BRANCH_FILE);
        Branch branch = readObject(BRANCH, Branch.class);

        File com = join(COMMITS_DIR, commitRef);
        Commit commit = readObject(com, Commit.class);

        List<String> committed = new ArrayList<>();
        for (HashMap.Entry<String, String> entry : headCommit.getMap().entrySet()) {
            committed.add(entry.getKey());
        }

        List<String> untracked = getUntracked(headCommit);
        if (!untracked.isEmpty()) {
            System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
            return;
        }

        for (Map.Entry<String, String> entry : commit.getMap().entrySet()) {
            File source = join(COMMITTED_DIR, entry.getValue());
            File target = join(CWD, entry.getKey());
            copyFile(source, target);
        }

        /* Any files that are tracked in the current commit but are not present in the given commit are deleted.*/
        for (String file : committed) {
            if (!commit.getMap().containsKey(file)) {
                File f = join(CWD, file);
                if (f.exists()) {
                    f.delete();
                }
            }
        }

        head.setCommitReference(commitRef);
        branch.getBranch().put(head.getBranch(), commitRef);

        writeObject(HEAD, head);
        writeObject(BRANCH, branch);

        /* Clear staging areas */
        clearStagingArea();
    }
    public static void mergebase(String givenBranch) {
        Branch branch = Branch.load();
        String givenBranchHeadCommitRef = branch.getBranch().get(givenBranch);

        Head head = Head.load();
        String HeadCommitRef = head.getCommitReference();

        String LCA = findLCA(givenBranch);
        System.out.println(LCA);
    }
    public static void merge(String givenBranch) {
        Branch branch = Branch.load();
        String givenBranchHeadCommitRef = branch.getBranch().get(givenBranch);

        Head head = Head.load();
        String HeadCommitRef = head.getCommitReference();

        String LCA = findLCA(givenBranch);

       if (givenBranchHeadCommitRef.equals(LCA)) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        }

        /* If head commit of the current branch is the last common ancestor, then fast-forward
        the current branch.
         */
        if (HeadCommitRef.equals(LCA)) {
            System.out.println("Current branch fast-forwarded.");
            branchFastForward(givenBranch, head);
            return;
        }

        condition1(LCA, givenBranch);
        mergeCommit(givenBranch);
    }

    /* Checkout the given branch, reset current branch head pointer to point to
    given branch's front commit.
     */
    public static void branchFastForward(String givenBranch, Head head) {
        String currentBranch = head.getBranch();
        System.out.println(currentBranch);
        checkoutBranch(givenBranch);
        System.out.println(currentBranch);
        head = Head.load();
        Branch branch = Branch.load();
        branch.getBranch().put(currentBranch, head.getCommitReference());
        head.setBranch(currentBranch);

        branch.save();
        head.save();

    }

    /* If a file that exists at split point, unchanged at current branch, but
    changed in the given branch, checkout file from the given branch front commit
    and add the file to the staging area;
     */
    public static void condition1(String LCA, String givenBranch) {
        Head head = Head.load();
        Branch branch = Branch.load();
        Commit commitSplit = Commit.load(LCA);
        Commit commitHead = Commit.load(head.getCommitReference());

        String givenCommitRef = branch.getBranch().get(givenBranch);
        Commit commitGiven = Commit.load(givenCommitRef);

        HashMap<String, String> inSpChangedInGiven = new HashMap<>();
        HashMap<String, String> inSpNotChangedInCurr = new HashMap<>();
        List<String> toBeCheckedOut = new ArrayList<>();

        commitSplit.getMap().forEach((fileSp, sha1Sp) -> {
            commitGiven.getMap().forEach((fileGiven, sha1Given) -> {
                if (fileSp.equals(fileGiven) && !sha1Sp.equals(sha1Given)) {
                    inSpChangedInGiven.put(fileGiven, sha1Given);
                }
            });
        });

        commitSplit.getMap().forEach((fileSp, sha1Sp) -> {
            commitHead.getMap().forEach((fileCurr, sha1Curr) -> {
                if (fileSp.equals(fileCurr) && sha1Sp.equals(sha1Curr)) {
                    inSpNotChangedInCurr.put(fileCurr, sha1Curr);
                }
            });
        });

        inSpChangedInGiven.forEach((fileGiven, sha1Given) -> {
            inSpNotChangedInCurr.forEach((fileCurr, sha1Curr) -> {
                if (fileGiven.equals(fileCurr)) {
                    checkoutCommit(givenCommitRef, fileGiven);
                    add(fileGiven);
                }
            });
        });

    }

    /* Create merge commit */
    public static void mergeCommit(String givenBranch) {
        Head head = Head.load();
        Branch branch = Branch.load();
        String givenCommitRef = branch.getBranch().get(givenBranch);
        Commit commit = Commit.load(head.getCommitReference());

        commit.setTimeStamp(new Date());
        commit.setMessage(String.format("Merged %s into %s", givenBranch, head.getBranch()));
        commit.setParentReference(head.getCommitReference());
        commit.setParentReference2(givenCommitRef);

        commit = commitFileOps(commit);

        head.setCommitReference(commit.sha1());
        branch.getBranch().put(head.getBranch(), commit.sha1());

        head.save();
        branch.save();

        commit.save();

    }


    public static String findLCA(String branch) {
        Head head = Head.load();
        Commit commit = Commit.load(head.getCommitReference());

        Branch br = Branch.load();
        Commit branchCommit = Commit.load(br.getBranch().get(branch));
        HashSet<String> branchSet = new HashSet<>();
        branchWalk2(branchCommit, branchSet);
        System.out.println(branchSet);
        String LCA = commmitWalk2(commit,branchSet);
        return LCA;
    }


    /* Breadth first search for last common ancestor */
    public static String commmitWalk2(Commit commit, HashSet<String> branchSet) {
        boolean foundLCA = false;
        String LCA = null;
        Queue<Commit> queue = new ArrayDeque<>();
        queue.add(commit);

        while(!foundLCA && !queue.isEmpty()) {
            commit = queue.poll();
            System.out.println(commit.sha1());
            if (branchSet.contains(commit.sha1())){
                LCA = commit.sha1();
                foundLCA = true;
            }

            if (commit.getParentReference() != null) {
                Commit commit1 = Commit.load(commit.getParentReference());
                queue.add(commit1);
            }
            if (commit.getParentReference2() != null) {
                Commit commit2 = Commit.load(commit.getParentReference2());
                queue.add(commit2);
            }
        }
        return LCA;
    }

    /* Breadth first search for all branch ancestors */
    public static void branchWalk2(Commit commit, HashSet<String> branchSet) {
        Queue<Commit> queue = new ArrayDeque<>();
        queue.add(commit);

        while(!queue.isEmpty()){
            branchSet.add(commit.sha1());
            commit = queue.poll();

            if (commit.getParentReference() != null) {
                Commit commit1 = Commit.load(commit.getParentReference());
                queue.add(commit1);
            }

            if (commit.getParentReference2() != null) {
                Commit commit2 = Commit.load(commit.getParentReference2());
                queue.add(commit2);
            }

        }
    }

    private static List<String> getUntracked(Commit commit) {
        List<String> committed = new ArrayList<>();
        for (HashMap.Entry<String, String> entry : commit.getMap().entrySet()) {
            committed.add(entry.getKey());
        }

        List<String> stagedForAddition = plainFilenamesIn(STAGING_DIR);
        List<String> stagedForRemoval = plainFilenamesIn(STAGING_FOR_REMOVAL_DIR);
        List<String> workingDirFiles = plainFilenamesIn(CWD);

        List<String> untracked = new ArrayList<>();
        for (String file : workingDirFiles) {
            if (!stagedForAddition.contains(file) && !committed.contains(file)) {
                untracked.add(file);
            }
            if (stagedForRemoval.contains(file)) {
                untracked.add(file);
            }
        }
        return untracked;
    }

    private static void clearStagingArea() {
        List<String> stagedForAddition = plainFilenamesIn(STAGING_DIR);
        List<String> stagedForRemoval = plainFilenamesIn(STAGING_FOR_REMOVAL_DIR);

        for (String file : stagedForAddition) {
            File f = join(STAGING_DIR, file);
            if (f.exists()) {
                f.delete();
            }
        }

        for (String file : stagedForRemoval) {
            File f = join(STAGING_FOR_REMOVAL_DIR, file);
            if (f.exists()) {
                f.delete();
            }
        }
    }
}
