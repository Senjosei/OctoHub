package com.fastaccess.ui.modules.repos.pull_requests.pull_request.details

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener
import com.evernote.android.state.State
import com.fastaccess.R
import com.fastaccess.data.dao.CommentRequestModel
import com.fastaccess.data.dao.FragmentPagerAdapterModel.Companion.buildForPullRequest
import com.fastaccess.data.dao.LabelModel
import com.fastaccess.data.dao.MilestoneModel
import com.fastaccess.data.dao.ReviewRequestModel
import com.fastaccess.data.dao.types.IssueState
import com.fastaccess.data.entity.PullRequest
import com.fastaccess.data.entity.User
import com.fastaccess.data.entity.dao.LoginDao
import com.fastaccess.data.entity.dao.PinnedPullRequestsDao
import com.fastaccess.helper.ActivityHelper
import com.fastaccess.helper.AnimHelper.mimicFabVisibility
import com.fastaccess.helper.BundleConstant
import com.fastaccess.helper.Bundler
import com.fastaccess.helper.InputHelper.isEmpty
import com.fastaccess.helper.Logger.e
import com.fastaccess.provider.scheme.LinkParserHelper.isEnterprise
import com.fastaccess.ui.adapter.FragmentsPagerAdapter
import com.fastaccess.ui.base.BaseActivity
import com.fastaccess.ui.base.BaseFragment
import com.fastaccess.ui.modules.editor.comment.CommentEditorFragment
import com.fastaccess.ui.modules.repos.RepoPagerActivity
import com.fastaccess.ui.modules.repos.RepoPagerMvp
import com.fastaccess.ui.modules.repos.extras.assignees.AssigneesDialogFragment
import com.fastaccess.ui.modules.repos.extras.labels.LabelsDialogFragment
import com.fastaccess.ui.modules.repos.extras.locking.LockIssuePrBottomSheetDialog.Companion.newInstance
import com.fastaccess.ui.modules.repos.extras.milestone.create.MilestoneDialogFragment
import com.fastaccess.ui.modules.repos.issues.create.CreateIssueActivity
import com.fastaccess.ui.modules.repos.pull_requests.pull_request.details.files.PullRequestFilesFragment
import com.fastaccess.ui.modules.repos.pull_requests.pull_request.details.timeline.timeline.PullRequestTimelineFragment
import com.fastaccess.ui.modules.repos.pull_requests.pull_request.merge.MergePullRequestDialogFragment
import com.fastaccess.ui.modules.reviews.changes.ReviewChangesFragment
import com.fastaccess.ui.modules.reviews.changes.ReviewChangesFragment.Companion.startForResult
import com.fastaccess.ui.widgets.AvatarLayout
import com.fastaccess.ui.widgets.FontTextView
import com.fastaccess.ui.widgets.ForegroundImageView
import com.fastaccess.ui.widgets.SpannableBuilder.Companion.builder
import com.fastaccess.ui.widgets.ViewPagerView
import com.fastaccess.ui.widgets.dialog.MessageDialogView
import com.fastaccess.ui.widgets.dialog.MessageDialogView.Companion.newInstance
import com.fastaccess.utils.setOnThrottleClickListener
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout

/**
 * Created by Kosh on 10 Dec 2016, 9:23 AM
 */
open class PullRequestPagerActivity :
    BaseActivity<PullRequestPagerMvp.View, PullRequestPagerPresenter>(),
    PullRequestPagerMvp.View {
    val startGist: ForegroundImageView? by lazy { viewFind(R.id.startGist) }
    private val forkGist: ForegroundImageView? by lazy { viewFind(R.id.forkGist) }
    val avatarLayout: AvatarLayout? by lazy { viewFind(R.id.avatarLayout) }
    val title: FontTextView? by lazy { viewFind(R.id.headerTitle) }
    val size: FontTextView? by lazy { viewFind(R.id.size) }
    val date: FontTextView? by lazy { viewFind(R.id.date) }
    val tabs: TabLayout? by lazy { viewFind(R.id.tabs) }
    val pager: ViewPagerView? by lazy { viewFind(R.id.pager) }
    val fab: FloatingActionButton? by lazy { viewFind(R.id.fab) }
    val detailsIcon: View? by lazy { viewFind(R.id.detailsIcon) }
    private val reviewsCount: FontTextView? by lazy { viewFind(R.id.reviewsCount) }
    private val prReviewHolder: CardView? by lazy { viewFind(R.id.prReviewHolder) }

    @State
    var isClosed = false

    @State
    var isOpened = false
    private var commentEditorFragment: CommentEditorFragment? = null

    private fun onTitleClick() {
        if (presenter!!.pullRequest != null && !isEmpty(
                presenter!!.pullRequest!!.title
            )
        ) newInstance(
            String.format("%s/%s", presenter!!.login, presenter!!.repoId),
            presenter!!.pullRequest!!.title!!, isMarkDown = false, hideCancel = true
        )
            .show(supportFragmentManager, MessageDialogView.TAG)
    }

    private fun onSubmitReviews() {
        addPrReview()
    }

    private fun onCancelReviews() {
        newInstance(
            getString(R.string.cancel_reviews), getString(R.string.confirm_message),
            false, Bundler.start()
                .put(BundleConstant.YES_NO_EXTRA, true)
                .put(BundleConstant.EXTRA_TYPE, true)
                .end()
        )
            .show(supportFragmentManager, MessageDialogView.TAG)
    }

    override fun layout(): Int {
        return R.layout.issue_pager_activity
    }

    override val isTransparent: Boolean
        get() = true

    override fun canBack(): Boolean {
        return true
    }

    override val isSecured: Boolean
        get() = false

    override fun providePresenter(): PullRequestPagerPresenter {
        return PullRequestPagerPresenter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        detailsIcon!!.setOnThrottleClickListener {
            onTitleClick()
        }

        viewFind<View>(R.id.submitReviews)!!.setOnThrottleClickListener {
            onSubmitReviews()
        }
        viewFind<View>(R.id.cancelReview)!!.setOnThrottleClickListener {
            onCancelReviews()
        }
        commentEditorFragment =
            supportFragmentManager.findFragmentById(R.id.commentFragment) as CommentEditorFragment?
        if (savedInstanceState == null) {
            presenter!!.onActivityCreated(intent)
        } else {
            if (presenter!!.pullRequest != null) onSetupIssue(false)
        }
        fab!!.hide()
        startGist!!.visibility = View.GONE
        forkGist!!.visibility = View.GONE
        if (presenter!!.showToRepoBtn()) showNavToRepoItem()
    }

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val data = it.data
        if (it.resultCode == RESULT_OK && data != null) {
            val bundle = data.extras
            val pullRequest: PullRequest? = bundle!!.getParcelable(BundleConstant.ITEM)
            if (pullRequest != null) {
                presenter!!.onUpdatePullRequest(pullRequest)
            } else {
                presenter!!.onRefresh()
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.pull_request_menu, menu)
        menu.findItem(R.id.merge).isVisible = false
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onNavToRepoClicked()
            return true
        }
        val pullRequest = presenter!!.pullRequest ?: return false
        if (item.itemId == R.id.share) {
            ActivityHelper.shareUrl(this, pullRequest.htmlUrl!!)
            return true
        } else if (item.itemId == R.id.closeIssue) {
            newInstance(
                if (pullRequest.state === IssueState.open) getString(R.string.close_issue) else getString(
                    R.string.re_open_issue
                ),
                getString(R.string.confirm_message),
                Bundler.start().put(BundleConstant.EXTRA, true).end()
            )
                .show(supportFragmentManager, MessageDialogView.TAG)
            return true
        } else if (item.itemId == R.id.lockIssue) {
            if (!presenter!!.isLocked) {
                newInstance()
                    .show(supportFragmentManager, MessageDialogView.TAG)
            } else {
                newInstance(
                    getString(R.string.unlock_issue), getString(R.string.unlock_issue_details),
                    Bundler.start().put(BundleConstant.EXTRA_TWO, true)
                        .put(BundleConstant.YES_NO_EXTRA, true)
                        .end()
                )
                    .show(supportFragmentManager, MessageDialogView.TAG)
            }
            return true
        } else if (item.itemId == R.id.labels) {
            LabelsDialogFragment.newInstance(
                if (presenter!!.pullRequest != null) presenter!!.pullRequest!!
                    .labels else null,
                presenter!!.repoId!!, presenter!!.login!!
            )
                .show(supportFragmentManager, "LabelsDialogFragment")
            return true
        } else if (item.itemId == R.id.edit) {
            CreateIssueActivity.startForResult(
                this,
                launcher,
                presenter!!.login!!,
                presenter!!.repoId!!,
                pullRequest,
                isEnterprise
            )
            return true
        } else if (item.itemId == R.id.milestone) {
            MilestoneDialogFragment.newInstance(
                presenter!!.login!!, presenter!!.repoId!!
            )
                .show(supportFragmentManager, "MilestoneDialogFragment")
            return true
        } else if (item.itemId == R.id.assignees) {
            AssigneesDialogFragment.newInstance(
                presenter!!.login!!,
                presenter!!.repoId!!,
                true
            )
                .show(supportFragmentManager, "AssigneesDialogFragment")
            return true
        } else if (item.itemId == R.id.reviewers) {
            AssigneesDialogFragment.newInstance(
                presenter!!.login!!,
                presenter!!.repoId!!,
                false
            )
                .show(supportFragmentManager, "AssigneesDialogFragment")
            return true
        } else if (item.itemId == R.id.merge) {
            if (presenter!!.pullRequest != null) {
                val msg = presenter!!.pullRequest!!.title
                MergePullRequestDialogFragment.newInstance(msg)
                    .show(supportFragmentManager, "MergePullRequestDialogFragment")
            }
        } else if (item.itemId == R.id.browser) {
            ActivityHelper.startCustomTab(this, pullRequest.htmlUrl!!)
            return true
        } else if (item.itemId == R.id.reviewChanges) {
            addPrReview()
            return true
        } else if (item.itemId == R.id.subscribe) {
            presenter!!.onSubscribeOrMute(false)
            return true
        } else if (item.itemId == R.id.mute) {
            presenter!!.onSubscribeOrMute(true)
            return true
        } else if (item.itemId == R.id.pinUnpin) {
            presenter!!.onPinUnpinPullRequest()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val closeIssue = menu.findItem(R.id.closeIssue)
        val lockIssue = menu.findItem(R.id.lockIssue)
        val milestone = menu.findItem(R.id.milestone)
        val labels = menu.findItem(R.id.labels)
        val assignees = menu.findItem(R.id.assignees)
        val edit = menu.findItem(R.id.edit)
        val editMenu = menu.findItem(R.id.editMenu)
        val merge = menu.findItem(R.id.merge)
        val reviewers = menu.findItem(R.id.reviewers)
        val pinUnpin = menu.findItem(R.id.pinUnpin)
        val isOwner = presenter!!.isOwner
        val isLocked = presenter!!.isLocked
        val isCollaborator = presenter!!.isCollaborator
        val isRepoOwner = presenter!!.isRepoOwner
        val isMergable = presenter!!.isMergeable
        merge.isVisible = isMergable && (isRepoOwner || isCollaborator)
        reviewers.isVisible = isRepoOwner || isCollaborator
        editMenu.isVisible = isOwner || isCollaborator || isRepoOwner
        milestone.isVisible = isCollaborator || isRepoOwner
        labels.isVisible = isCollaborator || isRepoOwner
        assignees.isVisible = isCollaborator || isRepoOwner
        edit.isVisible = isCollaborator || isRepoOwner || isOwner
        if (presenter!!.pullRequest != null) {
            val isPinned = PinnedPullRequestsDao.isPinned(
                presenter!!.pullRequest!!.id
            ).blockingGet()
            pinUnpin.icon = if (isPinned) ContextCompat.getDrawable(
                this,
                R.drawable.ic_pin_filled
            ) else ContextCompat.getDrawable(this, R.drawable.ic_pin)
            closeIssue.isVisible =
                isRepoOwner || (isOwner || isCollaborator) && presenter!!.pullRequest!!
                    .state === IssueState.open
            lockIssue.isVisible = isRepoOwner || isCollaborator && presenter!!.pullRequest!!
                .state === IssueState.open
            closeIssue.title =
                if (presenter!!.pullRequest!!.state === IssueState.closed) getString(R.string.re_open) else getString(
                    R.string.close
                )
            lockIssue.title =
                if (isLocked) getString(R.string.unlock_issue) else getString(R.string.lock_issue)
        } else {
            closeIssue.isVisible = false
            lockIssue.isVisible = false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onSetupIssue(update: Boolean) {
        hideProgress()
        if (presenter!!.pullRequest == null) {
            return
        }
        invalidateOptionsMenu()
        val pullRequest = presenter!!.pullRequest
        setTaskName(pullRequest!!.repoId + " - " + pullRequest.title)
        updateViews(pullRequest)
        if (update) {
            val issueDetailsView = pullRequestTimelineFragment
            if (issueDetailsView != null && presenter!!.pullRequest != null) {
                issueDetailsView.onUpdateHeader()
            }
        } else {
            if (pager!!.adapter == null) {
                pager!!.adapter = FragmentsPagerAdapter(
                    supportFragmentManager, buildForPullRequest(
                        this,
                        pullRequest
                    )
                )
                tabs!!.setupWithViewPager(pager)
                tabs!!.addOnTabSelectedListener(object :
                    TabLayout.ViewPagerOnTabSelectedListener(pager) {
                    override fun onTabReselected(tab: TabLayout.Tab) {
                        super.onTabReselected(tab)
                        onScrollTop(tab.position)
                    }
                })
            } else {
                onUpdateTimeline()
            }
        }
        pager!!.addOnPageChangeListener(object : SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                hideShowFab()
                super.onPageSelected(position)
            }
        })
        initTabs(pullRequest)
        hideShowFab()
        mimicFabVisibility(presenter!!.hasReviewComments(), prReviewHolder!!, null)
        reviewsCount!!.text = String.format("%s", presenter.commitComment.size)
    }

    override fun onScrollTop(index: Int) {
        if (pager == null || pager!!.adapter == null) return
        val fragment: Fragment = pager!!.adapter!!
            .instantiateItem(pager!!, index) as BaseFragment<*, *>
        if (fragment is BaseFragment<*, *>) {
            fragment.onScrollTop(index)
        }
    }

    override fun onMessageDialogActionClicked(isOk: Boolean, bundle: Bundle?) {
        super.onMessageDialogActionClicked(isOk, bundle)
        if (isOk) {
            if (bundle != null) {
                if (bundle.getBoolean(BundleConstant.EXTRA_TYPE)) {
                    hideAndClearReviews()
                    return
                }
            }
            presenter!!.onHandleConfirmDialog(bundle)
        }
    }

    override fun onSelectedLabels(labels: ArrayList<LabelModel>) {
        presenter!!.onPutLabels(labels)
    }

    override fun showSuccessIssueActionMsg(isClose: Boolean) {
        hideProgress()
        if (isClose) {
            isOpened = false
            isClosed = true
            showMessage(getString(R.string.success), getString(R.string.success_closed))
        } else {
            isOpened = true
            isClosed = false
            showMessage(getString(R.string.success), getString(R.string.success_re_opened))
        }
    }

    override fun showErrorIssueActionMsg(isClose: Boolean) {
        hideProgress()
        if (isClose) {
            showMessage(getString(R.string.error), getString(R.string.error_closing_issue))
        } else {
            showMessage(getString(R.string.error), getString(R.string.error_re_opening_issue))
        }
    }

    override fun onUpdateTimeline() {
        invalidateOptionsMenu()
        val pullRequestDetailsView = pullRequestTimelineFragment
        if (pullRequestDetailsView != null && presenter!!.pullRequest != null) {
            pullRequestDetailsView.onRefresh()
        }
    }

    override fun onMileStoneSelected(milestoneModel: MilestoneModel) {
        presenter!!.onPutMilestones(milestoneModel)
    }

    override fun onFinishActivity() {
        hideProgress()
        finish()
    }

    override fun onUpdateMenu() {
        invalidateOptionsMenu()
    }

    override fun onAddComment(comment: CommentRequestModel?) {
        presenter!!.onAddComment(comment!!)
        mimicFabVisibility(presenter!!.hasReviewComments(), prReviewHolder!!, null)
        reviewsCount!!.text = String.format("%s", presenter.commitComment.size)
        e(reviewsCount!!.text, prReviewHolder!!.visibility)
    }

    override fun onMerge(msg: String, mergeMethod: String) {
        presenter!!.onMerge(msg, mergeMethod)
    }

    override fun onNavToRepoClicked() {
        val intent = ActivityHelper.editBundle(
            RepoPagerActivity.createIntent(
                this, presenter!!.repoId!!,
                presenter!!.login!!, RepoPagerMvp.PULL_REQUEST
            ), isEnterprise
        )
        startActivity(intent)
        finish()
    }

    override fun finish() {
        val intent = Intent()
        intent.putExtras(
            Bundler.start()
                .put(BundleConstant.EXTRA, isClosed)
                .put(BundleConstant.EXTRA_TWO, isOpened)
                .end()
        )
        setResult(RESULT_OK, intent)
        super.finish()
    }

    override fun onSelectedAssignees(users: ArrayList<User>, isAssignees: Boolean) {
        hideProgress()
        presenter!!.onPutAssignees(users, isAssignees)
    }

    override val data: PullRequest?
        get() = presenter!!.pullRequest

    override fun onSendActionClicked(text: String, bundle: Bundle?) {
        val fragment = pullRequestTimelineFragment
        fragment?.onHandleComment(text, bundle)
    }

    private val pullRequestTimelineFragment: PullRequestTimelineFragment?
        get() = if (pager == null || pager!!.adapter == null) null else pager!!.adapter!!.instantiateItem(
            pager!!,
            0
        ) as PullRequestTimelineFragment

    override fun onTagUser(username: String) {
        commentEditorFragment!!.onAddUserName(username)
    }

    override fun onCreateComment(text: String, bundle: Bundle?) {
        commentEditorFragment!!.onCreateComment(text, bundle)
    }

    override fun onSuccessfullyReviewed() {
        hideAndClearReviews()
        pager!!.currentItem = 0
    }

    override fun onClearEditText() {
        if (commentEditorFragment != null) commentEditorFragment!!.commentText.setText(
            ""
        )
    }

    override fun getNamesToTag(): ArrayList<String>? {
        val fragment = pullRequestTimelineFragment
        return fragment?.namesToTag ?: ArrayList()
    }

    override fun onLock(reason: String) {
        presenter!!.onLockUnlockConversations(reason)
    }

    private fun hideAndClearReviews() {
        presenter.commitComment.clear()
        mimicFabVisibility(false, prReviewHolder!!, null)
        if (pager == null || pager!!.adapter == null) return
        val fragment = pager!!.adapter!!
            .instantiateItem(pager!!, 2) as PullRequestFilesFragment
        fragment.onRefresh()
    }

    private fun addPrReview() {
        val pullRequest = presenter!!.pullRequest ?: return
        val author =
            (if (pullRequest.user != null) pullRequest.user else if (pullRequest.head != null && pullRequest.head!!.author != null) pullRequest.head!!.author else pullRequest.user)
                ?: return
        val requestModel = ReviewRequestModel()
        requestModel.comments =
            if (presenter.commitComment.isEmpty()) null else presenter.commitComment
        requestModel.commitId = pullRequest.head!!.sha
        val isAuthor =
            LoginDao.getUser().blockingGet().or().login.equals(author.login, ignoreCase = true)
        startForResult(
            requestModel,
            presenter!!.repoId!!,
            presenter!!.login!!,
            pullRequest.number.toLong(),
            isAuthor,
            isEnterprise,
            pullRequest.merged
                    || pullRequest.state === IssueState.closed
        )
            .show(supportFragmentManager, ReviewChangesFragment::class.java.simpleName)
    }

    private fun initTabs(pullRequest: PullRequest) {
        val tab1 = tabs!!.getTabAt(0)
        val tab2 = tabs!!.getTabAt(1)
        val tab3 = tabs!!.getTabAt(2)
        if (tab3 != null) {
            tab3.text = builder()
                .append(getString(R.string.files))
                .append(" ")
                .append("(")
                .append(pullRequest.changedFiles.toString())
                .append(")")
        }
        if (tab2 != null) {
            tab2.text = builder()
                .append(getString(R.string.commits))
                .append(" ")
                .append("(")
                .append(pullRequest.commits.toString())
                .append(")")
        }
        if (tab1 != null) {
            tab1.text = builder()
                .append(getString(R.string.details))
                .append(" ")
                .append("(")
                .append(pullRequest.comments.toString())
                .append(")")
        }
    }

    private fun updateViews(pullRequest: PullRequest) {
        setTitle(String.format("#%s", pullRequest.number))
        if (supportActionBar != null) {
            supportActionBar!!.subtitle = pullRequest.repoId
        }
        date!!.text =
            builder().append(presenter!!.getMergeBy(pullRequest, applicationContext))
        size!!.visibility = View.GONE
        val userModel = pullRequest.user
        if (userModel != null) {
            title!!.text =
                builder().append(userModel.login).append("/").append(pullRequest.title)
            avatarLayout!!.setUrl(
                userModel.avatarUrl, userModel.login, false,
                isEnterprise(pullRequest.url)
            )
        } else {
            title!!.text = builder().append(pullRequest.title)
        }
        detailsIcon!!.visibility = View.VISIBLE
    }

    private fun hideShowFab() {
        if (presenter!!.isLocked && !presenter!!.isOwner && !presenter!!.isCollaborator) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .hide(commentEditorFragment!!).commit()
            return
        }
        if (pager!!.currentItem == 0) {
            supportFragmentManager.beginTransaction().show(commentEditorFragment!!)
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .commit()
        } else {
            supportFragmentManager.beginTransaction().hide(commentEditorFragment!!)
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .commit()
        }
    }

    companion object {
        @JvmOverloads
        fun createIntent(
            context: Context,
            repoId: String,
            login: String,
            number: Int,
            showRepoBtn: Boolean = false,
            isEnterprise: Boolean = false,
            commentId: Long = 0
        ): Intent {
            val intent = Intent(context, PullRequestPagerActivity::class.java)
            intent.putExtras(
                Bundler.start()
                    .put(BundleConstant.ID, number)
                    .put(BundleConstant.EXTRA, login)
                    .put(BundleConstant.EXTRA_TWO, repoId)
                    .put(BundleConstant.EXTRA_THREE, showRepoBtn)
                    .put(BundleConstant.IS_ENTERPRISE, isEnterprise)
                    .put(BundleConstant.EXTRA_SIX, commentId)
                    .end()
            )
            return intent
        }
    }
}
