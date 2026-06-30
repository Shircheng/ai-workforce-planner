import { useCallback, useEffect } from 'react'
import { useBlocker } from 'react-router-dom'

const DEFAULT_MESSAGE = 'Input will be lost if you leave this page.'

function useUnsavedChangesPrompt(when: boolean, message = DEFAULT_MESSAGE) {
  const blocker = useBlocker(when)

  useEffect(() => {
    if (!when) return

    function handleBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault()
      event.returnValue = message
      return message
    }

    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [message, when])

  const confirmNavigation = useCallback(() => {
    if (blocker.state === 'blocked') {
      blocker.proceed()
    }
  }, [blocker])

  const cancelNavigation = useCallback(() => {
    if (blocker.state === 'blocked') {
      blocker.reset()
    }
  }, [blocker])

  return {
    cancelNavigation,
    confirmNavigation,
    isBlocked: blocker.state === 'blocked',
    message,
  }
}

export default useUnsavedChangesPrompt